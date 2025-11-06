package agrotech;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@CsvRecord(separator = ",", skipFirstLine = true)
public class Sensor {

    @DataField(pos = 1, columnName = "id_sensor")
    private String idSensor;

    @DataField(pos = 2, columnName = "fecha")
    private String fecha;

    @DataField(pos = 3, columnName = "humedad")
    private double humedad;

    @DataField(pos = 4, columnName = "temperatura")
    private double temperatura;

    // Getters y setters
    public String getIdSensor() { return idSensor; }
    public void setIdSensor(String idSensor) { this.idSensor = idSensor; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public double getHumedad() { return humedad; }
    public void setHumedad(double humedad) { this.humedad = humedad; }

    public double getTemperatura() { return temperatura; }
    public void setTemperatura(double temperatura) { this.temperatura = temperatura; }
}