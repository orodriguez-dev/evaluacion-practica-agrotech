package agrotech;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.component.file.GenericFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

public class App extends RouteBuilder
{
    public static void main( String[] args ) throws Exception
    {
        Main main = new Main();
        main.configure().addRoutesBuilder(new App());
        main.run(args);

        // 🔹 Ejecutar FieldControl automáticamente al iniciar
        System.out.println("=== Ejecutando FieldControl para mostrar los últimos valores ===");
        main.getCamelTemplate().sendBody("direct:getLatestReadings", null);
    }

    @Override
    public void configure() {
        // Crear la tabla si no existe
        crearTablaSiNoExiste();

        ObjectMapper mapper = new ObjectMapper();
        //1. Transferencia de archivos (SensData → AgroAnalyzer)
        from("file:FlujoAgrotech/SensData?noop=true") // Lee CSV sin borrarlos
            .routeId("csv-to-json-sqlite")
            .log("Archivo leido: ${file:name}")
            .process(exchange -> {
                // Obtener GenericFile y el File real
                GenericFile<?> gf = exchange.getIn().getBody(GenericFile.class);
                File localFile = (File) gf.getFile();

                // Leer todo el contenido como String (UTF-8)
                String content = Files.readString(localFile.toPath(), StandardCharsets.UTF_8);

                // Separar líneas y quitar posibles líneas vacías
                String[] lines = Arrays.stream(content.split("\\r?\\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);

                if (lines.length < 2) {
                    // No hay datos (solo header o vacío)
                    exchange.getIn().setBody("[]");
                    return;
                }

                // Primer línea: headers
                String[] headers = lines[0].split(",");

                List<Map<String, String>> rows = new ArrayList<>();

                for (int i = 1; i < lines.length; i++) {
                    String[] values = lines[i].split(",", -1); // -1 para mantener campos vacíos
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int j = 0; j < headers.length && j < values.length; j++) {
                        row.put(headers[j].trim(), values[j].trim());
                    }
                    rows.add(row);

                    // Insertar en SQLite (por fila)
                    // 2. Base de datos compartida (AgroAnalyzer ↔ FieldControl)
                    String id_sensor = row.getOrDefault("id_sensor", "");
                    String fecha = row.getOrDefault("fecha", "");
                    String humedadStr = row.getOrDefault("humedad", "0");
                    String temperaturaStr = row.getOrDefault("temperatura", "0");

                    double humedad = tryParseDouble(humedadStr, 0.0);
                    double temperatura = tryParseDouble(temperaturaStr, 0.0);

                    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/AgroAnalyzer.sqlite")) {
                        String sql = "INSERT INTO lecturas (id_sensor, fecha, humedad, temperatura) VALUES (?, ?, ?, ?)";
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setString(1, id_sensor);
                        ps.setString(2, fecha);
                        ps.setDouble(3, humedad);
                        ps.setDouble(4, temperatura);
                        ps.executeUpdate();
                        ps.close();
                    }
                }

                // Convertir la lista de mapas a JSON
                String json = mapper.writeValueAsString(rows);

                // Poner el JSON en el body para que el siguiente to(...) lo escriba a archivo
                exchange.getIn().setBody(json);
            })
            .to("file:FlujoAgrotech/AgroAnalyzer?fileName=${file:name.noext}.json")
            .log("Archivo covertido y procesado: ${file:name}")
            //Ejecutar automáticamente FieldControl al terminar
            .to("direct:getLatestReadings");

        // ROUTE: FieldControl consulta últimos valores por sensor (direct)
        from("direct:getLatestReadings")
            .routeId("fieldcontrol-get-latest")
            .process(exchange -> {
                List<Map<String,Object>> result = new ArrayList<>();
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/AgroAnalyzer.sqlite")) {
                    String sql = """
                        SELECT id_sensor, fecha, humedad, temperatura
                        FROM (
                            SELECT 
                                id_sensor,
                                fecha,
                                humedad,
                                temperatura,
                                ROW_NUMBER() OVER (PARTITION BY id_sensor ORDER BY fecha DESC) AS rn
                            FROM lecturas
                        )
                        WHERE rn = 1
                        ORDER BY id_sensor;
                    """;

                    try (PreparedStatement ps = conn.prepareStatement(sql);
                        java.sql.ResultSet rs = ps.executeQuery()) {

                        while (rs.next()) {
                            Map<String,Object> row = new LinkedHashMap<>();
                            row.put("id_sensor", rs.getString("id_sensor"));
                            row.put("fecha", rs.getString("fecha"));
                            row.put("humedad", rs.getDouble("humedad"));
                            row.put("temperatura", rs.getDouble("temperatura"));
                            result.add(row);
                        }
                    }
                } catch (Exception e) {
                    // registra error y devuelve arreglo vacío
                    exchange.getMessage().setBody(String.format("Error consultando lecturas: %s", e.getMessage()));
                }
                // convierto a JSON y devuelvo en el body
                ObjectMapper localMapper = new ObjectMapper();
                String json = localMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                exchange.getMessage().setBody(json);

                // imprime en consola (evidencia visual)
                System.out.println("=== Últimos valores de sensores (FieldControl) ===");
                System.out.println(json);
        })
        .log("Consulta completada: últimos valores de sensores recuperados.");

        //3. Remote Procedure Call (RPC simulado con Apache Camel).
        // Cliente (FieldControl)
        from("direct:solicitarLectura")
            .routeId("rpc-cliente")
            .setHeader("id_sensor", simple("${body}"))
            .log("[CLIENTE] Solicitando lectura del sensor ${header.id_sensor}")
            .toD("direct:rpc.obtenerUltimo?timeout=2000")
            .log("[CLIENTE] Respuesta recibida: ${body}");

        // Servidor (AgroAnalyzer)
        from("direct:rpc.obtenerUltimo")
            .routeId("rpc-servidor")
            .log("[SERVIDOR] Solicitud recibida para sensor ${header.id_sensor}")
            .bean(ServicioAnalitica.class, "getUltimoValor");
    }

    // Crea la base de datos y la tabla si no existe
    private void crearTablaSiNoExiste() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:database/AgroAnalyzer.sqlite")) {
            File dir = new File("database");
            if (!dir.exists()) dir.mkdirs();

            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lecturas (
                    id_sensor VARCHAR(10) NOT NULL,
                    fecha TEXT NOT NULL,
                    humedad DOUBLE,
                    temperatura DOUBLE
                );
            """);
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Intenta convertir un String a double, devuelve defaultVal si falla
    private static double tryParseDouble(String s, double defaultVal) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
