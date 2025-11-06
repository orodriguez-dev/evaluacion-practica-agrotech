# 🌾 Proyecto de Integración Empresarial – AgroTech Solutions S.A.

## 📘 Descripción General

Este proyecto implementa un **prototipo funcional de integración empresarial** entre tres sistemas de **AgroTech Solutions S.A.**, una empresa que utiliza sensores agrícolas para optimizar el uso del agua y los recursos del campo.

Los tres sistemas principales son:

- **SensData** → recopila lecturas de sensores en formato CSV.  
- **AgroAnalyzer** → procesa los datos y los almacena en una base de datos compartida.  
- **FieldControl** → consulta los valores más recientes para controlar el sistema de riego.

El objetivo es **automatizar el flujo de información** aplicando **patrones clásicos de integración empresarial**, eliminando procesos manuales y mejorando la confiabilidad.

## ⚙️ Patrones de Integración Implementados

### 🔹 1️⃣ File Transfer (SensData → AgroAnalyzer)

Se desarrolló una ruta con **Apache Camel** que:
- Lee los archivos CSV desde la carpeta `SensData`.
- Convierte su contenido a formato **JSON**.
- Transfiere los archivos automáticamente a la carpeta `AgroAnalyzer`.

**Ventaja:** Permite mover los datos entre sistemas sin conexión directa ni APIs.  
**Evidencia:** Archivos JSON generados automáticamente y logs del flujo.

### 🔹 2️⃣ Shared Database (AgroAnalyzer ↔ FieldControl)

Se implementó una base de datos **SQLite** como repositorio común:

```sql
CREATE TABLE IF NOT EXISTS sensores (
  id_sensor TEXT NOT NULL,
  fecha TEXT NOT NULL,
  humedad REAL,
  temperatura REAL
);

**AgroAnalyzer** inserta los datos procesados.  
**FieldControl** consulta los valores más recientes.

🧩 **Ventaja:** Ambos sistemas acceden a la misma fuente de información sin duplicar datos.  
⚠️ **Riesgo:** La concurrencia o bloqueo puede afectar el rendimiento si varios sistemas escriben simultáneamente.

## 🔹 3️⃣ Remote Procedure Call (RPC Simulado con Apache Camel)

Se simuló una comunicación **síncrona** entre **FieldControl** y **AgroAnalyzer** usando rutas `direct:` de **Apache Camel**.

### 💻 Cliente (FieldControl)

```java
from("direct:solicitarLectura")
    .routeId("rpc-cliente")
    .setHeader("id_sensor", simple("${body}"))
    .log("[CLIENTE] Solicitando lectura del sensor ${header.id_sensor}")
    .toD("direct:rpc.obtenerUltimo?timeout=2000")
    .log("[CLIENTE] Respuesta recibida: ${body}");
    
🧰 Tecnologías Utilizadas
Componente	Herramienta / Versión
☕ Lenguaje	Java 25
🐫 Framework	Apache Camel 4.x
📦 Gestor de dependencias	Maven
💾 Base de datos	SQLite
🧑‍💻 IDE recomendado	Visual Studio Code
🧾 Logging	Apache Camel Logs