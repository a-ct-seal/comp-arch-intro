import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ElfParser {
    public static final int SECTION_HEADER_SIZE = 40; // size of section header in bytes
    public static final int SYMBOL_SIZE = 16; // size of symbol in symbol table in bytes
    public static final String SYMBOL_TABLE_OUTPUT_HEADER = "\n.symtab\nSymbol Value          	Size Type  	  Bind 	   Vis   	 Index Name\n";
    public static final String SYMBOL_TABLE_LINE_TEMPLATE = "[%4d] 0x%-13X %5d %-8s %-8s %-8s %6s %s\n";
    private final int[] elf;
    private final OutputStream out;
    private int shStringTableStart, stringTableStart, stringTableSize;
    private int symbolTableStart, symbolTableSize;
    private int textStart, textSize, textAddr;
    private final Map<Integer, String> labels;
    private String symtab;

    public ElfParser(List<Integer> elf, OutputStream out) {
        this.elf = new int[elf.size()];
        for (int i = 0; i < elf.size(); i++) {
            this.elf[i] = elf.get(i);
        }
        this.out = out;
        labels = new HashMap<>();
    }

    public void parse() throws UnsupportedFileFormatException, IOException {
        if (!(elf[0] == 0x7f &&
                elf[1] == 0x45 &&
                elf[2] == 0x4c &&
                elf[3] == 0x46)) {
            throw new UnsupportedFileFormatException("File is not ELF");
        }
        if (!(elf[4] == 1)) {
            throw new UnsupportedFileFormatException("File is not 32-bit");
        }
        if (!(elf[18] == 0xf3 && elf[19] == 0x00)) {
            throw new UnsupportedFileFormatException("File is not RISC-V");
        }
        if (!(elf[5] == 1)) {
            throw new UnsupportedFileFormatException("File is not little endian");
        }
        parseSectionHeaderTable(); // we need .text .symtab .strtab sections
        parseSymbolTable();
        parseText();
        out.write(symtab.getBytes());
    }

    private void parseSectionHeaderTable() {
        int sectionTablePosition = get4Bytes(32);
        int sectionTableSize = get2Bytes(48);
        shStringTableStart = get4Bytes(sectionTablePosition + get2Bytes(50) * SECTION_HEADER_SIZE + 16);
        for (int i = sectionTablePosition; i < sectionTablePosition + sectionTableSize * SECTION_HEADER_SIZE; i += SECTION_HEADER_SIZE) {
            ElfSectionHeader header = new ElfSectionHeader();
            header.name = getSectionName(get4Bytes(i));
            header.type = get4Bytes(i + 4);
            header.offset = get4Bytes(i + 16);
            header.size = get4Bytes(i + 20);
            if (header.type == 0x2) {
                symbolTableStart = header.offset;
                symbolTableSize = header.size;
            }
            if (header.name.equals(".text")) {
                textStart = header.offset;
                textSize = header.size;
                textAddr = get4Bytes(i + 12);
            } else if (header.name.equals(".strtab")) {
                stringTableStart = header.offset;
                stringTableSize = header.size;
            }
        }
    }

    private void parseText() throws IOException {
        out.write(".text\n".getBytes());
        RISCVParser parser = new RISCVParser(elf, out, labels, textStart, textSize, textAddr);
        parser.parseText();
    }

    private void parseSymbolTable() {
        StringBuilder resToWrite = new StringBuilder();
        resToWrite.append(SYMBOL_TABLE_OUTPUT_HEADER);
        int idx = 0;
        for (int i = symbolTableStart; i < symbolTableStart + symbolTableSize; i += SYMBOL_SIZE) {
            ElfSymbol symbol = new ElfSymbol();
            symbol.name = getSymbolName(get4Bytes(i));
            symbol.value = get4Bytes(i + 4);
            symbol.size = get4Bytes(i + 8);
            int info = elf[i + 12];
            symbol.type = getSymbolType((info) & 0xf);
            symbol.bind = getSymbolBind((info) >> 4);
            symbol.vis = getSymbolVis(elf[i + 13]);
            symbol.index = getSymbolIndex(get2Bytes(i + 14));
            if (Objects.equals(symbol.type, "FUNC")) {
                labels.put(symbol.value, symbol.name);
            }
            resToWrite.append(String.format(SYMBOL_TABLE_LINE_TEMPLATE, idx, symbol.value, symbol.size, symbol.type,
                    symbol.bind, symbol.vis, symbol.index, symbol.name));
            idx++;
        }
        symtab = resToWrite.toString();
    }

    private String getSymbolType (int type) {
        return switch (type) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "";
        };
    }

    private String getSymbolBind (int bind) {
        return switch (bind) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "";
        };
    }

    private String getSymbolVis (int vis) {
        return switch (vis) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            case 4 -> "EXPORTED";
            case 5 -> "SINGLETON";
            case 6 -> "ELIMINATE";
            default -> "";
        };
    }

    private String getSymbolIndex (int index) {
        return switch (index) {
            case 0 -> "UNDEF";
            case 0xff00 -> "LORESERVE";
            case 0xff01 -> "AFTER";
            case 0xff02 -> "AMD64_LCOMMON";
            case 0xff1f -> "HIPROC";
            case 0xff20 -> "LOOS";
            case 0xff3f -> "LOSUNW";
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xffff -> "XINDEX";
            default -> Integer.toString(index);
        };
    }

    private String getSymbolName (int offset) {
        if (offset == 0) {
            return "";
        }
        StringBuilder name = new StringBuilder();
        for (int idx = stringTableStart + offset; elf[idx] != 0; idx++) {
            name.append((char) elf[idx]);
        }
        return name.toString();
    }

    private String getSectionName(int offset) {
        StringBuilder name = new StringBuilder();
        for (int idx = shStringTableStart + offset; elf[idx] != 0; idx++) {
            name.append((char) elf[idx]);
        }
        return name.toString();
    }

    private int get4Bytes(int idx) {
        return elf[idx + 3] * 256 * 256 * 256 + elf[idx + 2] * 256 * 256 + elf[idx + 1] * 256 + elf[idx];
    }

    private int get2Bytes(int idx) {
        return elf[idx + 1] * 256 + elf[idx];
    }
}
