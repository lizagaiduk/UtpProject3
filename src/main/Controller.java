package main;
import models.Bind;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptContext;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Controller {
    private final Object model;
    private final Map<String, double[]> data;
    private final Map<String, double[]> results;
    private List<Integer> years;

    public Controller(String modelName) {
        this.data = new LinkedHashMap<>();
        this.results = new LinkedHashMap<>();
        this.years = new ArrayList<>();
        try {
            System.out.println("Attempting to load model: " + modelName);
            Class<?> c= Class.forName(modelName);
            this.model = c.getDeclaredConstructor().newInstance();
            System.out.println("Model loaded successfully: " + modelName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Class not found: " + modelName, e);
        }
    }

    public Controller readDataFrom(String fname) {
        try (Scanner scanner = new Scanner(new File(fname))) {
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
                    data.put(varName, values);
                    setField(varName, values);
                }
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found: " + fname, e);
        } catch (Exception e) {
            throw new RuntimeException("Error reading data from file: " + fname, e);
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
            System.out.println("Running model...");
            model.getClass().getMethod("run").invoke(model);
            System.out.println("Model executed successfully.");
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
            System.out.println("Adding to script engine: " + key + " = " + Arrays.toString(value));
            engine.put(key, value);
        });

        int LL = (int) getField();
        engine.put("LL", LL);
        System.out.println("Adding to script engine: LL = " + LL);

        try {
            System.out.println("Executing script: " + script);
            engine.eval(script);
            updateResultsFromScript(engine);
            System.out.println("Script executed successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Error executing script: " + script, e);
        }
    }

    private String roundValue(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        if (value < 1) {
            return String.format("%.3f", value);
        } else if(value>1&&value<10){
            return String.format("%.2f", value);
        } else {
            return String.format("%.1f", value);
        }
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
                    .mapToObj(this::roundValue)
                    .collect(Collectors.joining("\t")));
            tsv.append("\n");
        });
        return tsv.toString();
    }
}
