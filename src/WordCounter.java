import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WordCounter {
    public static final Path pathToFolder = Paths.get("");      // User specifies the folder of text files
    public static final Path pathToOutput = Paths.get("");      // User specifies the folder of the output file
    public static final int THREAD_COUNT = 1;                          // User specifies number of threads, 0 is default

    public static ConcurrentHashMap<String, Integer[]> table = new ConcurrentHashMap<>();
    public static int maxStringLength = 0;
    public static File[] files;

    static class Counter implements Runnable {
        private final File f;
        private final Integer threadFile;

        public Counter(File f, Integer threadFile) {
            this.f = f;
            this.threadFile = threadFile;
        }

        public File getF() { return this.f; }
        public Integer getThreadFile() { return this.threadFile; }

        @Override
        public void run() {
            try {
                FileInputStream fis = new FileInputStream(getF());
                InputStreamReader inStream = new InputStreamReader(fis);
                BufferedReader reader = new BufferedReader(inStream);
                StringBuilder s = new StringBuilder();
                int i;
                while ((i = reader.read()) != -1) {
                    char c = (char) i;
                    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                        c = Character.toLowerCase(c);
                        s.append(c);
                    } else {
                        if (s.length() > 0)
                            updateConcurrentMap(s.toString(), getThreadFile());
                        s = new StringBuilder();
                    }
                }
            } catch (IOException e) {
                System.out.printf("Unable to read from directory: %s%n", pathToFolder);
            }
        }

        public static synchronized void updateConcurrentMap(String s, Integer threadFile) {
            int i;
            if (!table.containsKey(s)) {
                Integer[] arr = new Integer[files.length + 1];
                for (i = 0; i < arr.length; i++) {
                    arr[i] = 0;     // Initialize array
                }

                arr[threadFile] = 1;     // Initialize current file's count
                arr[files.length] = 1;  // Initialize total count
                table.put(s, arr);

                if (maxStringLength < s.length())
                    maxStringLength = s.length();

            } else {
                Integer[] arr = table.get(s);
                arr[threadFile]++;
                arr[files.length]++;
                table.replace(s, arr);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean readAllFiles;
        File folder = pathToFolder.toFile();
        files = folder.listFiles();
        sortFileNames();

        try {
            if (files.length == 0)
                throw new IOException();

            ExecutorService e;
            if (THREAD_COUNT < 0) {
                System.out.println("Cannot run the WordCounter with less than 0 threads.");
                return;
            } else if (THREAD_COUNT == 1) {
                e = Executors.newFixedThreadPool(1);
                for (int i = 0; i < files.length; i++) {
                    e.execute(new Counter(files[i], i));    // Run with current file
                }

                e.shutdown();
                readAllFiles = e.awaitTermination(1, TimeUnit.MINUTES);
            } else {
                e = Executors.newFixedThreadPool(Integer.min(THREAD_COUNT, files.length));
                for (int i = 0; i < files.length; i++) {
                    e.execute(new Counter(files[i], i));    // Run with current file
                }

                e.shutdown();
                readAllFiles = e.awaitTermination(1, TimeUnit.MINUTES);
            }

            if (readAllFiles)
                writeToFile();
        } catch (IOException e) {
            System.out.printf("Unable to read from directory: %s%n", pathToFolder);
        }
    }

    public static void sortFileNames() {
        Arrays.sort(files, Comparator.comparing(File::getName));
    }

    public static void writeToFile() {
        StringBuilder output = new StringBuilder();
        int i, j;
        TreeMap<String, Integer[]> map = new TreeMap<>(table);
        Set<String> set = map.keySet();

        for (i = 0; i <= maxStringLength; i++)
            output.append(" ");

        for (i = 0; i < files.length; i++)  {
            String name = files[i].getName().substring(0, files[i].getName().indexOf(".txt"));
            output.append(name);
            output.append("\t");
        }

        output.append("total" + "\n");
        int count = 0;
        int total = table.keySet().size();
        for (String str : set) {
            output.append(str);
            int l = str.length();
            while (l <= maxStringLength) {
                output.append(" ");
                l++;
            }
            count++;

            for (i = 0; i < files.length + 1; i++) {
                output.append(map.get(str)[i]);
                if (i < files.length) {
                    String name = files[i].getName().substring(0, files[i].getName().indexOf(".txt"));
                    String integer = "" + map.get(str)[i];
                    for (j = 0; j < (name.length() + 4 - integer.length()); j++) {
                        output.append(" ");
                    }
                }
            }

            if (count < total)
                output.append("\n");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(pathToOutput)) {
            writer.write(output.toString());
        } catch (IOException e) {
            System.out.printf("Unable to write to directory: %s%n", pathToOutput);
        }
    }
}
