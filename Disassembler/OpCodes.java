import java.util.List;
import java.util.Map;

public class OpCodes {
    public static Map<Integer, Map<Integer, Map<Integer, String>>> Rcodes = Map.ofEntries(
            Map.entry(0b0010011, Map.of(
                    0b001, Map.of(0b0000000, "slli"),
                    0b101, Map.of(0b0000000, "srli", 0b0100000, "srai")
            )),
            Map.entry(0b0110011, Map.of(
                    0b000, Map.of(0b0000000, "add", 0b0100000, "sub", 0b0000001, "mul"),
                    0b001, Map.of(0b0000000, "sll", 0b0000001, "mulh"),
                    0b010, Map.of(0b0000000, "slt", 0b0000001, "mulhsu"),
                    0b011, Map.of(0b0000000, "sltu", 0b0000001, "mulhu"),
                    0b100, Map.of(0b0000000, "xor", 0b0000001, "div"),
                    0b101, Map.of(0b0000000, "srl", 0b0100000, "sra", 0b0000001, "divu"),
                    0b110, Map.of(0b0000000, "or", 0b0000001, "rem"),
                    0b111, Map.of(0b0000000, "and", 0b0000001, "remu")
            ))
    );

    public static Map<Integer, Map<Integer, String>> Icodes = Map.ofEntries(
            Map.entry(0b1100111, Map.of(0b000, "jalr")),
            Map.entry(0b0000011, Map.of(
                    0b000, "lb",
                    0b001, "lh",
                    0b010, "lw",
                    0b100, "lbu",
                    0b101, "lhu"
            )),
            Map.entry(0b0010011, Map.of(
                    0b000, "addi",
                    0b010, "slti",
                    0b011, "sltiu",
                    0b100, "xori",
                    0b110, "ori",
                    0b111, "andi"
            ))
    );

    public static Map<Integer, Map<Integer, String>> Scodes = Map.ofEntries(
            Map.entry(0b0100011, Map.of(
                    0b000, "sb",
                    0b001, "sh",
                    0b010, "sw"
            ))
    );

    public static Map<Integer, Map<Integer, String>> Bcodes = Map.ofEntries(
            Map.entry(0b1100011, Map.of(
                    0b000, "beq",
                    0b001, "bne",
                    0b100, "blt",
                    0b101, "bge",
                    0b110, "bltu",
                    0b111, "bgeu"
            ))
    );

    public static Map<Integer, String> Ucodes = Map.of(
            0b0110111, "lui",
            0b0010111, "auipc"
    );

    public static Map<Integer, String> Jcodes = Map.of(
            0b1101111, "jal"
    );

    public static Map<Integer, Character> types = Map.ofEntries(
            Map.entry(0b0110111, 'U'),
            Map.entry(0b0010111, 'U'),
            Map.entry(0b1101111, 'J'),
            Map.entry(0b1100111, 'I'),
            Map.entry(0b1100011, 'B'),
            Map.entry(0b0000011, 'I'),
            Map.entry(0b0100011, 'S'),
            Map.entry(0b0010011, 'I'),
            Map.entry(0b0110011, 'R')
    );

    public static List<String> registerNames = List.of(
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2", "s0", "s1", "a0", "a1", "a2", "a3", "a4",
            "a5", "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    );
}