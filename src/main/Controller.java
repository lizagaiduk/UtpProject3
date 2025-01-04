package main;



import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

public class Controller {
    private final Object model;
    private final Map<String, double[]> results;
    private final List<Integer> years;

    public Controller(String modelName) {
        this.results = new LinkedHashMap<>();
        this.years = new ArrayList<>();
        try {
            Class<?> c= Class.forName(modelName);
            this.model = c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Class not found: " + modelName, e);
        }
    }

    public Controller readDataFrom(String fileName) {
        try (Scanner scanner = new Scanner(new File(fileName))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("LATA")) {
                    String[] yearParts = line.split("\\s+");
                    for (int i = 1; i < yearParts.length; i++) {
                        years.add(Integer.parseInt(yearParts[i]));
                    }
                    setField("LL", years.size());
                } else if (!line.isEmpty()) {
                    String[] parts = line.split("\\s+");
                    String varName = parts[0];
                    double[] values = parseValue(parts);
                    setField(varName, values);
                }
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + fileName, e);
        }
        return this;
    }

    private double[] parseValue(String[] parts) {
        int LL = (int) getField();
        double[] values = new double[LL];
        for (int i = 1; i < parts.length && i <= LL; i++) {
            values[i - 1] = Double.parseDouble(parts[i]);
        }
        if (parts.length - 1 < LL) {
            double lastValue = values[parts.length - 2];
            Arrays.fill(values, parts.length - 1, LL, lastValue);
        }
        return values;
    }

    public void runModel() {
        try {
            model.getClass().getMethod("run").invoke(model);
            updateResults();
        } catch (Exception e) {
            throw new RuntimeException("Error executing model", e);
        }
    }

    private void updateResults() {
        Arrays.stream(model.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Bind.class))
                .forEach(field -> {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(model);
                        if (value instanceof double[]) {
                            results.put(field.getName(), (double[]) value);
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Error accessing field: " + field.getName(), e);
                    }
                });
    }

    public void runScriptFromFile(String fileName) {
        try {
            String script = Files.readString(Paths.get(fileName));
            runScript(script);
        } catch (IOException e) {
            throw new RuntimeException("Error reading script file: " + fileName, e);
        }
    }


    public void runScript(String script) {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");

        if (engine == null) {
            throw new RuntimeException("Groovy scriptEngine not found. Ensure groovy library is on the classpath.");
        }

        results.forEach((key, value) -> {
            engine.put(key, value);
        });

        int LL = (int) getField();
        engine.put("LL", LL);

        try {
            engine.eval(script);
            updateResultsFromScript(engine);
        } catch (Exception e) {
            throw new RuntimeException("Error executing script: " + script, e);
        }
    }

    private String roundValue(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        DecimalFormat formatter;

        if (value >= 10) {
            formatter = new DecimalFormat("#,##0.#", symbols);
        } else if (value >= 1) {
            formatter = new DecimalFormat("#,##0.##", symbols);
        } else {
            formatter = new DecimalFormat("#,##0.###", symbols);
        }
       return  formatter.format(value);
    }

    private void setField(String fieldName, Object value) {
        try {
            Field field = Arrays.stream(model.getClass().getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(Bind.class) && f.getName().equals(fieldName))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchFieldException("Field not found: " + fieldName));
            field.setAccessible(true);
            field.set(model, value);
        } catch (Exception e) {
            throw new RuntimeException("Error setting field: " + fieldName, e);
        }
    }

    private Object getField() {
        try {
            Field field = Arrays.stream(model.getClass().getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(Bind.class) && f.getName().equals("LL"))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchFieldException("Field not found: " + "LL"));
            field.setAccessible(true);
            return field.get(model);
        } catch (Exception e) {
            throw new RuntimeException("Error getting field: " + "LL", e);
        }
    }


    private void updateResultsFromScript(ScriptEngine engine) {
        engine.getBindings(ScriptContext.ENGINE_SCOPE).forEach((key, value) -> {
            if (value instanceof double[]) {
                results.put(key, (double[]) value);
            }
        });
    }

    public String getResultsAsTsv() {
        if (years.isEmpty()) {
            throw new IllegalStateException("Years list is empty. Ensure data is loaded using readDataFrom().");
        }

        StringBuilder tsv = new StringBuilder("LATA");
        years.forEach(year -> tsv.append("\t").append(year));
        tsv.append("\n");

        results.forEach((key, value) -> {
            tsv.append(key).append("\t");
            tsv.append(Arrays.stream(value)
                    .mapToObj( val-> roundValue(val))
                    .collect(Collectors.joining("\t")));
            tsv.append("\n");
        });
        return tsv.toString();
    }
}
