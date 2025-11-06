package agrotech;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.csv.BindyCsvDataFormat;
import org.apache.camel.main.Main;

public class App extends RouteBuilder
{
    public static void main( String[] args ) throws Exception
    {
        Main main = new Main();
        main.configure().addRoutesBuilder(new App());
        main.run(args);
    }

    @Override
    public void configure() {
        // Configure Bindy for CSV unmarshalling
        BindyCsvDataFormat bindyCsv = new BindyCsvDataFormat(Sensor.class);

        from("file:FlujoAgrotech/SensData?noop=true") // Lee CSV sin borrarlos
            .log("Archivo leido: ${file:name}")
            .unmarshal(bindyCsv) // Deserializa el contenido del archivo CSV.
            .log("Deserializa el contenido del archivo CSV.")
            .marshal().json()  // Convierte Lista -> JSON
            .to("file:FlujoAgrotech/AgroAnalyzer?fileName=${file:name.noext}.json")
            .log("Archivo procesado: ${file:name}");
    }
}
