import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class RISCVParser {
    private static final String LabelStrTemplate = "%08x   <%s>:\n";
    private static final String ThreeArgumentInstructionTemplate = "   %05x:\t%08x\t%7s\t%s, %s, %s\n";
    private static final String ThreeArgumentBranchInstructionTemplate = "   %05x:\t%08x\t%7s\t%s, %s, 0x%05x <%s>\n";
    private static final String TwoArgumentJalInstructionTemplate = "   %05x:\t%08x\t%7s\t%s, 0x%05x <%s>\n";
    private static final String TwoArgumentUtypeInstructionTemplate = "   %05x:\t%08x\t%7s\t%s, 0x%x\n";
    private static final String ZeroArgumentInstructionTemplate = "   %05x:\t%08x\t%7s\n";
    private static final String LoadStoreInstructionTemplate = "   %05x:\t%08x\t%7s\t%s, %s(%s)\n";
    private final int[] elf;
    private final OutputStream out;
    private final int textStart, textSize, textAddr;
    private final Map<Integer, String> labels;
    private int labelNum = 0;

    public RISCVParser(int[] elf, OutputStream out, Map<Integer, String> labels, int textStart, int textSize, int textAddr) {
        this.elf = elf;
        this.out = out;
        this.labels = new HashMap<>(labels);
        this.textStart = textStart;
        this.textSize = textSize;
        this.textAddr = textAddr - textStart;
    }

    public void parseText() throws IOException {
        List<String> commands = new ArrayList<>();
        for (int i = textStart; i < textStart + textSize; i += 4) {
            commands.add(parseLine(i));
        }
        for (int i = textStart; i < textStart + textSize; i += 4) {
            checkForLabel(i);
            out.write(commands.get((i - textStart) / 4).getBytes());
        }
    }

    private void checkForLabel(int idx) throws IOException {
        if (labels.containsKey(idx + textAddr)) {
            out.write(String.format(LabelStrTemplate, idx + textAddr, labels.get(idx + textAddr)).getBytes());
        }
    }

    private String getLabel(int value) {
        if (labels.containsKey(value)) {
            return labels.get(value);
        }
        labelNum++;
        labels.put(value, "L" + (labelNum - 1));
        return "L" + (labelNum - 1);
    }

    private String parseLine(int idx) {
        StringBuilder out = new StringBuilder();
        boolean[] code = convertToBits(get4Bytes(idx));
        int opcode = getBits(code, 0, 7);
        if (get4Bytes(idx) == 0b00000000000000000000000001110011) {
            out.append(String.format(ZeroArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), "ecall"));
            return out.toString();
        } else if (get4Bytes(idx) == 0b00000000000100000000000001110011) {
            out.append(String.format(ZeroArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), "ebreak"));
            return out.toString();
        }
        if (!OpCodes.types.containsKey(opcode)) {
            out.append(String.format(ZeroArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), "unknown_instruction"));
            return out.toString();
        }
        char type = OpCodes.types.get(opcode);
        int immSign = code[31] ? (-1) : 1;
        if (type == 'R') {
            String rd = OpCodes.registerNames.get(getBits(code, 7, 12));
            int funct3 = getBits(code, 12, 15);
            String rs1 = OpCodes.registerNames.get(getBits(code, 15, 20));
            int funct7 = getBits(code, 25, 32);
            String command = OpCodes.Rcodes.get(opcode).get(funct3).get(funct7);
            if (opcode == 0b0010011) {
                // shift
                int shamt = getBits(code, 20, 25);
                out.append(String.format(ThreeArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd, rs1, shamt));
                return out.toString();
            }
            String rs2 = OpCodes.registerNames.get(getBits(code, 20, 25));
            out.append(String.format(ThreeArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd, rs1, rs2));
            return out.toString();
        }
        if (type == 'I') {
            String rd = OpCodes.registerNames.get(getBits(code, 7, 12));
            int funct3 = getBits(code, 12, 15);
            String rs1 = OpCodes.registerNames.get(getBits(code, 15, 20));
            int imm = reverseImm(getBits(code,20, 31), immSign, 11);
            String command = OpCodes.Icodes.get(opcode).get(funct3);
            if (opcode == 0b0000011 || opcode == 0b1100111) {
                // load command or jalr
                out.append(String.format(LoadStoreInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd, imm, rs1));
            } else {
                out.append(String.format(ThreeArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd, rs1, imm));
            }
            return out.toString();
        }
        if (type == 'S') {
            int funct3 = getBits(code, 12, 15);
            String rs1 = OpCodes.registerNames.get(getBits(code, 15, 20));
            String rs2 = OpCodes.registerNames.get(getBits(code, 20, 25));
            int imm = reverseImm(getBits(code, 7, 12) + (getBits(code, 25, 31) << 5), immSign, 11);
            String command = OpCodes.Scodes.get(opcode).get(funct3);
            if (opcode == 0b0100011) {
                // store command
                out.append(String.format(LoadStoreInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rs2, imm, rs1));
            } else {
                out.append(String.format(ThreeArgumentInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rs1, rs2, imm));
            }
            return out.toString();
        }
        if (type == 'B') {
            int funct3 = getBits(code, 12, 15);
            String rs1 = OpCodes.registerNames.get(getBits(code, 15, 20));
            String rs2 = OpCodes.registerNames.get(getBits(code, 20, 25));
            int imm = reverseImm((getBits(code, 8, 12) << 1) +
                    (getBits(code, 25, 31) << 5) +
                    (getBits(code, 7, 8) << 11), immSign, 12);
            String command = OpCodes.Bcodes.get(opcode).get(funct3);
            out.append(String.format(ThreeArgumentBranchInstructionTemplate,
                    idx + textAddr, get4Bytes(idx), command, rs1, rs2, idx + textAddr + imm, getLabel(idx + textAddr + imm)));
            return out.toString();
        }
        if (type == 'U') {
            String rd = OpCodes.registerNames.get(getBits(code, 7, 12));
            int imm = reverseImm(getBits(code, 12, 31), immSign, 31);
            String command = OpCodes.Ucodes.get(opcode);
            out.append(String.format(TwoArgumentUtypeInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd, imm));
            return out.toString();
        }
        if (type == 'J') {
            // jal
            String rd = OpCodes.registerNames.get(getBits(code, 7, 12));
            int imm = reverseImm((getBits(code, 21, 25) << 1) + (getBits(code, 25, 31) << 5) +
                    (getBits(code, 20, 21) << 11 )+ (getBits(code, 12, 20) << 12), immSign, 20);
            String command = OpCodes.Jcodes.get(opcode);
            out.append(String.format(TwoArgumentJalInstructionTemplate, idx + textAddr, get4Bytes(idx), command, rd,
                    idx + textAddr + imm, getLabel(idx + textAddr + imm)));
        }
        return out.toString();
    }

    private boolean[] convertToBits(int x) {
        boolean[] res = new boolean[32];
        for (int i = 0; i < 32; i++) {
            res[i] = false;
        }
        String s = Integer.toBinaryString(x);
        for (int i = 32 - s.length(); i < 32; i++) {
            res[31 - i] = (s.charAt(i - 32 + s.length()) == '1');
        }
        return res;
    }

    private int reverseImm(int imm, int sign, int bitSize) {
        if (sign == 1) {
            return imm;
        }
        return ((1 << bitSize) - imm) * sign;
    }

    private int getBits(boolean[] n, int start, int end) {
        StringBuilder binNum = new StringBuilder();
        for (int i = end - 1; i >= start; i--) {
            binNum.append(n[i] ? '1' : '0');
        }
        return Integer.parseInt(binNum.toString(), 2);
    }

    private int get4Bytes(int idx) {
        return elf[idx + 3] * 256 * 256 * 256 + elf[idx + 2] * 256 * 256 + elf[idx + 1] * 256 + elf[idx];
    }
}