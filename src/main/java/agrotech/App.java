package agrotech;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;

public class App 
{
    public static void main( String[] args )
    {
        Main main = new Main();
        main.configure().addRoutesBuilder(new App());
        main.run(args);
    }

    @Override
    public void configure() {
        from("file:/Users/alejo/Documents/evaluacion-practica-agrotech/src/input?noop=true")
        .log("Procesando archivo: ${file:name}")
        .to("file:/Users/alejo/Documents/evaluacion-practica-agrotech/src/output");
    }
}
