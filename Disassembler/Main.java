import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(InputStream input, OutputStream output) throws IOException, UnsupportedFileFormatException {
        List<Integer> file = new ArrayList<>();
        int new_byte = input.read();
        while (new_byte != -1) {
            file.add(new_byte);
            new_byte = input.read();
        }
        ElfParser parser = new ElfParser(file, output);
        parser.parse();
    }

    public static void main(String[] args) {
        try {
            InputStream input = new FileInputStream(args[0]);
            try {
                OutputStream output = new FileOutputStream(args[1]);
                try {
                    main(input, output);
                } catch (UnsupportedFileFormatException e) {
                    System.out.println("Unsupported file format: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("I/O error: " + e.getMessage());
                }
            } catch (FileNotFoundException e) {
                System.out.println("Output file not found: " + e.getMessage());
            }
        } catch (FileNotFoundException e) {
            System.out.println("Input file not found: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Something went wrong, I give up: " + e.getMessage());
        }
    }
}