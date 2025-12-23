# Revisión de Arquitectura para Migración Java → Go
## Proyecto: Novo Reporte Usuarios PLD (Banorte)

**Versión actual**: 20200422
**Fecha de revisión**: 23/12/2025
**Propósito**: Documentar la arquitectura actual en Java para facilitar la migración a Go

---

## 📋 Tabla de Contenidos

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Arquitectura Actual](#arquitectura-actual)
3. [Flujo Funcional Completo](#flujo-funcional-completo)
4. [Componentes del Sistema](#componentes-del-sistema)
5. [Base de Datos](#base-de-datos)
6. [Archivos de Salida](#archivos-de-salida)
7. [Configuración y Dependencias](#configuración-y-dependencias)
8. [Controles y Validaciones](#controles-y-validaciones)
9. [Riesgos Identificados](#riesgos-identificados)
10. [Plan de Migración a Go](#plan-de-migración-a-go)
11. [Tareas de Migración](#tareas-de-migración)

---

## 📊 Resumen Ejecutivo

### Descripción del Sistema
Aplicación batch Java que genera reportes mensuales de usuarios finales para cumplimiento de PLD (Prevención de Lavado de Dinero) para Banorte. El sistema consulta usuarios de dos fuentes (afiliación directa y lotes de emisión), genera un archivo delimitado por pipes y envía notificaciones por email.

### Métricas del Proyecto
- **Líneas de código**: ~1,400 líneas Java
- **Clases principales**: 8
- **Archivos de configuración**: 7
- **Dependencias JAR**: 12
- **Tablas de BD**: 4 (2 fuentes + 2 maestros)
- **Formato de salida**: Pipe-delimited (|), encoding ISO-8859-1

### Complejidad de Migración
- **Nivel**: Medio
- **Esfuerzo estimado**: La migración requiere reimplementación completa sin timeline específico
- **Riesgo principal**: Compatibilidad de encoding ISO-8859-1 y formato exacto del archivo

---

## 🏗️ Arquitectura Actual

### Estructura de Directorios

```
novo_reporte_usuarios_pld/
├── src/main/
│   ├── java/com/novo/
│   │   ├── database/           # Capa de acceso a datos
│   │   │   ├── dbconfig.java       (112 líneas) - Configuración BD
│   │   │   └── dbinterface.java    (736 líneas) - JDBC wrapper
│   │   ├── main/               # Orquestación y notificaciones
│   │   │   ├── Main.java           (120 líneas) - Entry point
│   │   │   └── SendMail.java       (101 líneas) - Cliente SMTP
│   │   ├── processor/          # Lógica de negocio
│   │   │   ├── FileProcessor.java  (91 líneas) - Generación archivo
│   │   │   └── UsuarioProcessor.java (55 líneas) - Consulta usuarios
│   │   └── utils/              # Utilidades
│   │       ├── Constant.java       (15 líneas) - Constantes globales
│   │       └── Utils.java          (160 líneas) - Funciones helper
│   └── resources/
│       └── log4j.properties    # Configuración logging
├── config/
│   ├── constant_config.properties   # Configuración general
│   ├── constant_mail.properties     # Configuración SMTP
│   ├── constant_process.properties  # Validaciones (no usadas)
│   ├── constant_queries.properties  # Queries SQL
│   └── logo.png                     # Logo para email
├── lib/                        # 12 JARs (JDBC, Mail, Logging)
├── log/                        # Logs generados
├── arcrespuesta/               # Archivos de salida
├── parametros/                 # Configs alternativas
├── oracle.properties           # Conexión BD
└── build.gradle                # Build configuration
```

### Diagrama de Capas

```
┌─────────────────────────────────────────────────┐
│           Main.java (Orquestador)               │
│  - Inicialización                               │
│  - Manejo de errores                            │
│  - Coordinación de componentes                  │
└───────────────┬─────────────────┬───────────────┘
                │                 │
        ┌───────▼──────┐   ┌──────▼──────┐
        │ FileProcessor │   │  SendMail   │
        │  - createFile │   │ - sendEmail │
        └───────┬───────┘   └─────────────┘
                │
        ┌───────▼────────────┐
        │  UsuarioProcessor  │
        │  - getGeneral()    │
        └───────┬────────────┘
                │
        ┌───────▼──────────┐
        │   dbinterface    │
        │ - executeQuery() │
        │ - nextRecord()   │
        └───────┬──────────┘
                │
        ┌───────▼──────────┐
        │   Oracle DB      │
        │ 172.24.6.105:1398│
        └──────────────────┘
```

---

## 🔄 Flujo Funcional Completo

### Secuencia de Ejecución Detallada

```
┌──────────────────────────────────────────────────────────────┐
│ 1. INICIALIZACIÓN                                            │
├──────────────────────────────────────────────────────────────┤
│ Main.main()                                                  │
│   ├─> Cargar constant_config.properties                     │
│   ├─> Cargar constant_mail.properties                       │
│   ├─> Crear nombre archivo:                                 │
│   │   arcrespuesta/TDD_Empresarial_23_12_2025.txt          │
│   └─> Log: "Inicio proceso [version 20200422]"             │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ 2. CONEXIÓN A BASE DE DATOS                                  │
├──────────────────────────────────────────────────────────────┤
│ dbconfig.loadConfig("oracle.properties")                    │
│   ├─> Host: 172.24.6.105                                   │
│   ├─> Puerto: 1398                                         │
│   ├─> BD: UAT                                              │
│   └─> Usuario: NOVO04005                                   │
│                                                              │
│ dbinterface.dbinic()                                        │
│   ├─> DriverManager.getConnection()                        │
│   ├─> SI ERROR:                                            │
│   │   ├─> enviarAlerta(pemail)                            │
│   │   └─> System.exit(0) ⚠️                               │
│   └─> Log: "Conexión exitosa"                             │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ 3. CONSULTA DE USUARIOS                                      │
├──────────────────────────────────────────────────────────────┤
│ UsuarioProcessor.getGeneral(dbinterface)                    │
│   ├─> Leer SQL_QUERY_GENERAL de properties                 │
│   ├─> Aplicar filtros de fecha:                            │
│   │   ├─> SQL_FEC_ACTUAL = SI                             │
│   │   │   → TO_CHAR(SYSDATE,'MM/YYYY')                    │
│   │   └─> SQL_FEC_ESPECIFICA = NO                         │
│   │                                                         │
│   ├─> Ejecutar query UNION ALL:                           │
│   │   ├─> Fuente 1: NOVO_AFILIACION (NA)                 │
│   │   │   ├─> JOIN MAESTRO_PLASTICO_TEBCA (MPT)          │
│   │   │   ├─> JOIN MAESTRO_CLIENTES_TEBCA (MCT)          │
│   │   │   ├─> WHERE MPT.CON_ESTATUS NOT IN (0,4,9)       │
│   │   │   └─> WHERE TO_CHAR(NA.FECHA_REG,'MM/YYYY')      │
│   │   │       = TO_CHAR(SYSDATE,'MM/YYYY')               │
│   │   │                                                    │
│   │   └─> Fuente 2: NOVO_LOTE_EMI (NLE)                  │
│   │       ├─> JOIN MAESTRO_PLASTICO_TEBCA (MPT)          │
│   │       ├─> JOIN MAESTRO_CLIENTES_TEBCA (MCT)          │
│   │       ├─> WHERE MPT.CON_ESTATUS NOT IN (0,4,9)       │
│   │       └─> WHERE TO_CHAR(NLE.DTFECHAPROCESO,'MM/YYYY')│
│   │           = TO_CHAR(SYSDATE,'MM/YYYY')               │
│   │                                                        │
│   ├─> Iterar resultados con db.nextRecord():              │
│   │   ├─> Extraer 12 campos pipe-delimited               │
│   │   └─> Agregar a ArrayList<String>                    │
│   │                                                        │
│   ├─> SI ERROR query:                                     │
│   │   └─> lista.add("ERROR_BD")                          │
│   │                                                        │
│   └─> Retornar ArrayList                                  │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ 4. GENERACIÓN DE ARCHIVO                                     │
├──────────────────────────────────────────────────────────────┤
│ FileProcessor.createFile(dbinterface)                       │
│   ├─> Obtener lista de UsuarioProcessor                    │
│   │                                                         │
│   ├─> Crear BufferedWriter:                               │
│   │   ├─> Ruta: arcrespuesta/TDD_Empresarial_DD_MM_YYYY.txt│
│   │   └─> Encoding: ISO-8859-1 ⚠️                        │
│   │                                                         │
│   ├─> SI lista.isEmpty():                                 │
│   │   └─> SI CABECERA_VACIO = SI:                        │
│   │       └─> Escribir solo cabecera                      │
│   │                                                         │
│   ├─> SI lista tiene datos:                               │
│   │   ├─> Escribir cabecera (12 campos):                 │
│   │   │   CtaPrincipalEmpresarial|NombreCteCtaEmpresarial│
│   │   │   |Tarjeta|CURP|PrimerNombre|SegundoNombre|      │
│   │   │   ApellidoPaterno|ApellidoMaterno|FechaNacimiento│
│   │   │   |Genero|Email|Telefono\r\n                     │
│   │   │                                                    │
│   │   └─> Para cada registro:                            │
│   │       ├─> Split por pipe (|)                         │
│   │       ├─> Transformar fecha posición 8:              │
│   │       │   dd-MM-yy → dd/MM/yyyy                      │
│   │       │   Ejemplo: 01-06-18 → 01/06/2018            │
│   │       │                                               │
│   │       └─> Escribir línea + \r\n                      │
│   │                                                        │
│   ├─> Cerrar BufferedWriter                              │
│   └─> Retornar lista                                     │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ 5. NOTIFICACIONES EMAIL                                      │
├──────────────────────────────────────────────────────────────┤
│ SI lista contiene "ERROR_BD":                               │
│   ├─> Main.enviarAlerta(pemail)                            │
│   │   ├─> Asunto: "Alerta para Reporte..."               │
│   │   ├─> Mensaje: "Se presentaron fallas..."            │
│   │   └─> SendMail.sendEmailHtml()                        │
│   └─> System.exit(0) ⚠️                                   │
│                                                             │
│ SINO:                                                       │
│   └─> Main.enviarNotificaciones(pemail, cantidadUsuarios) │
│       ├─> Asunto: "Detalle de Reporte Usuarios..."       │
│       ├─> Mensaje HTML:                                   │
│       │   ├─> Fecha/hora actual                          │
│       │   ├─> "Nro de usuarios: XXX"                     │
│       │   └─> Logo embebido (config/logo.png)            │
│       └─> SendMail.sendEmailHtml()                        │
└──────────────────────────────────────────────────────────────┘
                            ↓
┌──────────────────────────────────────────────────────────────┐
│ 6. CIERRE                                                    │
├──────────────────────────────────────────────────────────────┤
│ dbinterface.dbend()                                         │
│   ├─> connection.close()                                   │
│   ├─> statement.close()                                    │
│   └─> resultSet.close()                                    │
│                                                             │
│ Log: "Fin proceso"                                         │
└──────────────────────────────────────────────────────────────┘
```

### Escenarios de Ejecución

#### Escenario 1: Ejecución Exitosa con Usuarios
```
Input:  Mes actual tiene usuarios nuevos
Query:  Retorna 150 registros
Output: TDD_Empresarial_23_12_2025.txt (151 líneas: 1 cabecera + 150 datos)
Email:  "Nro de usuarios enviados: 150"
Exit:   Normal
```

#### Escenario 2: Ejecución Exitosa sin Usuarios
```
Input:  Mes actual NO tiene usuarios nuevos
Query:  Retorna 0 registros
Output: TDD_Empresarial_23_12_2025.txt (1 línea: solo cabecera)
Email:  "Nro de usuarios enviados: 0"
Exit:   Normal
```

#### Escenario 3: Error de Conexión BD
```
Input:  BD no disponible o credenciales incorrectas
Error:  dbinic() retorna != 0
Output: NO se genera archivo
Email:  "Se presentaron fallas... BD no disponible"
Exit:   System.exit(0) - TERMINACIÓN ABRUPTA
```

#### Escenario 4: Error en Query
```
Input:  Query tiene error de sintaxis o tabla no existe
Error:  executeQuery() retorna != 0
Output: NO se genera archivo
Email:  "Se presentaron fallas... BD no disponible"
Exit:   System.exit(0) - TERMINACIÓN ABRUPTA
```

---

## 🧩 Componentes del Sistema

### 1. Main.java (com.novo.main.Main)
**Ubicación**: [src/main/java/com/novo/main/Main.java](src/main/java/com/novo/main/Main.java)
**Líneas**: 120
**Responsabilidad**: Orquestador principal de la aplicación

#### Métodos Públicos
```java
public static void main(String[] args)
// Entry point de la aplicación
// - Carga configuraciones
// - Inicializa conexión BD
// - Ejecuta FileProcessor
// - Envía notificaciones
// - Cierra recursos

public static void enviarNotificaciones(Properties pemail, int cantidad)
// Envía email de éxito con conteo de usuarios
// Parámetros:
//   - pemail: Properties con configuración SMTP
//   - cantidad: Número de usuarios procesados

public static void enviarAlerta(Properties pemail)
// Envía email de alerta en caso de error
// Parámetro:
//   - pemail: Properties con configuración SMTP
```

#### Flujo de main()
```java
1. Cargar constant_config.properties → pconfigFile
2. Cargar constant_mail.properties → pemail
3. Construir nombre archivo de salida
4. Inicializar dbinterface con oracle.properties
5. Validar dbo.rc == 0 (config cargada)
   → SI error: enviarAlerta() + exit(0)
6. Validar dbo.dbinic() == 0 (conexión exitosa)
   → SI error: enviarAlerta() + exit(0)
7. Ejecutar FileProcessor.createFile(dbo)
8. SI lista contiene "ERROR_BD": enviarAlerta()
   SINO: enviarNotificaciones(pemail, lista.size())
9. dbo.dbend() - Cerrar conexión
```

#### Dependencias
- `dbconfig` - Configuración BD
- `dbinterface` - Cliente JDBC
- `FileProcessor` - Generación archivo
- `SendMail` - Cliente SMTP
- `Utils` - Helpers
- `Constant` - Constantes

---

### 2. FileProcessor.java (com.novo.processor.FileProcessor)
**Ubicación**: [src/main/java/com/novo/processor/FileProcessor.java](src/main/java/com/novo/processor/FileProcessor.java)
**Líneas**: 91
**Responsabilidad**: Generación del archivo de reporte

#### Método Principal
```java
public static ArrayList<String> createFile(dbinterface db)
// Crea archivo de reporte con datos de usuarios
// Parámetro:
//   - db: Instancia de dbinterface con conexión activa
// Retorna:
//   - ArrayList<String> con registros procesados
//   - Lista vacía si no hay datos
//   - ["ERROR_BD"] si hubo error en query
```

#### Lógica de Transformación de Fecha
```java
// CRÍTICO: Esta transformación debe replicarse exactamente en Go
String fecha = info[8];  // Formato recibido: "dd-MM-yy"
Date objSDF = new SimpleDateFormat("dd-MM-yy").parse(fecha);
SimpleDateFormat objSDF2 = new SimpleDateFormat("dd/MM/yyyy");
String fechaTransformada = objSDF2.format(objSDF);
// Resultado: "dd/MM/yyyy"

// Ejemplo:
// Input:  "01-06-18"
// Output: "01/06/2018"
```

#### Manejo de Archivos Vacíos
```java
if (lista.isEmpty()) {
    if (pconfigFile.getProperty("CABECERA_VACIO").equals("SI")) {
        bw.write(cabecera + "\r\n");  // Escribe solo cabecera
    }
    // SI "CABECERA_VACIO" = "NO", archivo queda vacío
}
```

#### Encoding Crítico
```java
// ISO-8859-1 (Latin-1) requerido
OutputStreamWriter osw = new OutputStreamWriter(
    new FileOutputStream(oFile),
    "ISO-8859-1"  // ⚠️ NO usar UTF-8
);
BufferedWriter bw = new BufferedWriter(osw);
```

---

### 3. UsuarioProcessor.java (com.novo.processor.UsuarioProcessor)
**Ubicación**: [src/main/java/com/novo/processor/UsuarioProcessor.java](src/main/java/com/novo/processor/UsuarioProcessor.java)
**Líneas**: 55
**Responsabilidad**: Consulta de usuarios desde BD

#### Método Principal
```java
public static ArrayList<String> getGeneral(dbinterface db)
// Ejecuta query SQL y retorna usuarios como strings pipe-delimited
// Parámetro:
//   - db: Instancia de dbinterface con conexión activa
// Retorna:
//   - ArrayList<String> con registros en formato:
//     "campo1|campo2|...|campo12"
//   - ["ERROR_BD"] si executeQuery() falló
```

#### Lógica de Filtros de Fecha
```java
String query = Utils.getProperties(Constant.CONSTANTS_QUERIES)
                    .getProperty("SQL_QUERY_GENERAL");

// SI SQL_FEC_ACTUAL = "SI":
// La query ya contiene: TO_CHAR(SYSDATE,'MM/YYYY')
// No se requiere modificación

// SI SQL_FEC_ESPECIFICA != "NO":
// Reemplazar placeholders con fecha específica:
String diaIni = pconfigFile.getProperty("SQL_FEC_ESPECIFICA_DIA_INI");
String mesIni = pconfigFile.getProperty("SQL_FEC_ESPECIFICA_MES_INI");
String anioIni = pconfigFile.getProperty("SQL_FEC_ESPECIFICA_ANIO_INI");
// ... (código similar para DIA_FIN, MES_FIN, ANIO_FIN)
```

#### Formato de Datos Retornados
```java
// Cada registro es un String concatenado con pipes:
// Posición 0: ID_EXT_EMP (Cuenta Principal Empresarial)
// Posición 1: NOM_CLIENTE (Nombre Cliente)
// Posición 2: NOTARJETA/NRO_TARJETA (Tarjeta)
// Posición 3: IDPERSONA/ID_EXT_PER (CURP)
// Posición 4: NOMBRE1/NOMBRES (Primer Nombre)
// Posición 5: NOMBRE2/NOMBRES2 (Segundo Nombre)
// Posición 6: APELLIDO1/APELLIDOS (Apellido Paterno)
// Posición 7: APELLIDO2/APELLIDOS2 (Apellido Materno)
// Posición 8: Fecha construida de IDPERSONA (dd-MM-yy)
// Posición 9: Género decodificado (HOMBRE/MUJER)
// Posición 10: CORREO/EMAIL en UPPER()
// Posición 11: TELEFONO2/TELF_CELULAR
```

---

### 4. dbinterface.java (com.novo.database.dbinterface)
**Ubicación**: [src/main/java/com/novo/database/dbinterface.java](src/main/java/com/novo/database/dbinterface.java)
**Líneas**: 736
**Responsabilidad**: Abstracción de acceso a base de datos JDBC

#### Métodos Utilizados en el Proyecto

```java
public int dbinic()
// Inicializa conexión a BD
// Retorna: 0 si éxito, !=0 si error

public int executeQuery(String query)
// Ejecuta query SQL SELECT
// Parámetro: SQL query como String
// Retorna: 0 si éxito, !=0 si error
// Postcondición: Cursor posicionado ANTES del primer registro

public int nextRecord()
// Avanza al siguiente registro del ResultSet
// Retorna: 0 si hay más registros, !=0 si no hay más
// Similar a: rs.next() en JDBC

public String getFieldString(String field)
// Obtiene valor de columna como String
// Parámetro: Nombre de la columna
// Retorna: Valor del campo, "" si es NULL

public int dbend()
// Cierra conexión y libera recursos
// Retorna: 0 si éxito
```

#### Métodos NO Utilizados (Disponibles pero No Llamados)
```java
// Transactions
public void beginTransaction()
public void commit()
public void rollback()
public Savepoint setSavepoint()

// DML
public int ejecutarQueryToHash(String query)
public int ejecutarInsert(String tabla, ArrayList valores)
public int ejecutarUpdate(String tabla, ArrayList valores, String condicion)
public int eliminarRegistros(String condicion)

// Stored Procedures
public int executeProcedure(String nombreProcedure)

// Prepared Statements
public int executeQuery(String query, ArrayList<Object> params)
```

#### Arquitectura Interna
```java
private Connection conexion;      // JDBC Connection
private Statement statement;      // JDBC Statement (NO PreparedStatement)
private ResultSet resultado;      // JDBC ResultSet
private dbconfig configuracion;   // Config loader

// Patrón de uso:
// 1. new dbinterface(propsFile) → Carga config
// 2. dbinic() → DriverManager.getConnection()
// 3. executeQuery() → statement.executeQuery()
// 4. while (nextRecord() == 0) → rs.next()
// 5.   getFieldString() → rs.getString()
// 6. dbend() → close all
```

#### ⚠️ Problemas Identificados
```java
// 1. NO usa PreparedStatement → Vulnerable a SQL Injection
//    (Mitigado: query viene de properties, no de usuario)

// 2. NO usa Connection Pooling
//    Abre/cierra conexión en cada ejecución

// 3. Statement reutilizable
//    Mismo statement para múltiples queries

// 4. Manejo de NULL
if (fieldString == null || fieldString.equalsIgnoreCase("null")) {
    fieldString = "";  // Convierte NULL a string vacío
}
```

---

### 5. SendMail.java (com.novo.main.SendMail)
**Ubicación**: [src/main/java/com/novo/main/SendMail.java](src/main/java/com/novo/main/SendMail.java)
**Líneas**: 101
**Responsabilidad**: Envío de correos electrónicos vía SMTP

#### Método Principal Utilizado
```java
public void sendEmailHtml(String contenido)
// Envía email HTML con logo embebido
// Parámetro:
//   - contenido: HTML body del email
// Configuración desde constant_mail.properties:
//   - mail.smtp.host
//   - de (sender)
//   - para (destinatarios, separados por ;)
//   - asunto
//   - IMAGE_PATH (logo.png)
```

#### Configuración SMTP
```java
Properties props = new Properties();
props.put("mail.smtp.host", host);  // No authentication
props.put("mail.smtp.port", "25");  // Port 25 sin TLS
props.put("mail.debug", "false");

Session session = Session.getInstance(props);
// ⚠️ NO usa autenticación moderna (OAuth2, TLS)
```

#### Estructura del Email HTML
```html
<html>
<head>
  <style>
    font-family: Arial;
    font-size: 12px;
  </style>
</head>
<body>
  <p><img src='cid:imagen' width='150'/></p>  <!-- Logo embebido -->
  <p><b>Fecha/hora:</b> DD/MM/YYYY HH:MM:SS</p>
  <p>{contenido}</p>
  <p>Cordialmente,<br/>Equipo Técnico</p>
</body>
</html>
```

#### Formato de Destinatarios
```java
// constant_mail.properties:
para=operacionesit@novopayment.com;otro@example.com

// Parsing:
InternetAddress[] toAddress = InternetAddress.parse(para, false);
// Separador: punto y coma (;)
```

#### ⚠️ Riesgos de Seguridad
```
1. SMTP sin TLS/SSL → Tráfico en texto plano
2. Credenciales en properties sin encriptar
3. Puerto 25 → Puede ser bloqueado por firewalls modernos
4. No valida certificados del servidor
```

---

### 6. Utils.java (com.novo.utils.Utils)
**Ubicación**: [src/main/java/com/novo/utils/Utils.java](src/main/java/com/novo/utils/Utils.java)
**Líneas**: 160
**Responsabilidad**: Funciones utilitarias

#### Métodos Utilizados
```java
public static Properties getProperties(String fileName)
// Carga archivo .properties
// Parámetro: Nombre del archivo (sin ruta)
// Busca en: directorio actual
// Retorna: Properties object

public static String getDateFile()
// Retorna fecha actual en formato: dd_MM_yyyy
// Ejemplo: "23_12_2025"
// Uso: Nombre del archivo de salida

public static String getDate()
// Retorna fecha actual en formato: yyyy-MM-dd
// Ejemplo: "2025-12-23"
// Uso: Logs
```

#### Métodos NO Utilizados (Disponibles)
```java
// Encriptación AES (definido pero nunca llamado)
public static String cifrar(String sinCifrar, String llave)
public static String descifrar(String cifrado, String llave)
// ⚠️ Llave hardcodeada: "novopayment02017"

// String manipulation
public static String completarString(String pCadena, int pLongitud, String pCaracter, String pTipo)
public static String truncarString(String pCadena, int pLongitud)

// Split con límite
public static String[] splitString(String cadena, String delimitador, int limite)
```

#### Implementación getDateFile()
```java
public static String getDateFile() {
    DateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy");
    Date date = new Date();
    return dateFormat.format(date);
}
// Resultado: "23_12_2025"
```

---

### 7. dbconfig.java (com.novo.database.dbconfig)
**Ubicación**: [src/main/java/com/novo/database/dbconfig.java](src/main/java/com/novo/database/dbconfig.java)
**Líneas**: 112
**Responsabilidad**: Cargar configuración de BD

#### Método Principal
```java
public void loadConfig(String archivo)
// Carga archivo .properties con configuración de BD
// Busca en:
//   1. Directorio actual
//   2. ../parametros/
//   3. {catalina.home}/parametros/
// Si encuentra "encripted=SI" en properties:
//   → Desencripta password con AES
//   → Llave: "novopayment02017"
```

#### Propiedades Requeridas
```properties
# oracle.properties
bd_host=172.24.6.105       # Hostname o IP
bd_port=1398               # Puerto
bd_name=UAT                # Nombre de BD/SID
bd_user=NOVO04005          # Usuario
bd_password=novo04005      # Password (texto plano)
bd_driver=oracle.jdbc.driver.OracleDriver
TypeDrv=2                  # 1=Informix, 2=Oracle
```

#### Desencriptación de Password (No Usado Actualmente)
```java
// Si en properties existe:
encripted=SI

// Entonces:
String passwordEncriptado = props.getProperty("bd_password");
String passwordClaro = Utils.descifrar(passwordEncriptado, "novopayment02017");
// ⚠️ Llave AES hardcodeada en código fuente
```

---

### 8. Constant.java (com.novo.utils.Constant)
**Ubicación**: [src/main/java/com/novo/utils/Constant.java](src/main/java/com/novo/utils/Constant.java)
**Líneas**: 15
**Responsabilidad**: Constantes globales de la aplicación

#### Constantes Definidas
```java
public class Constant {
    public static final String VERSION = "20200422";
    public static final String PROGRAM = "novo_reporte_usuarios_pld";

    // Archivos de configuración
    public static final String ORACLE_BD = "oracle.properties";
    public static final String CONSTANTS_CONFIG = "config//constant_config.properties";
    public static final String CONSTANTS_QUERIES = "config//constant_queries.properties";
    public static final String CONSTANTS_MAIL = "config//constant_mail.properties";
}
```

#### Uso en el Código
```java
// Main.java
Properties pconfigFile = Utils.getProperties(Constant.CONSTANTS_CONFIG);

// UsuarioProcessor.java
String query = Utils.getProperties(Constant.CONSTANTS_QUERIES)
                    .getProperty("SQL_QUERY_GENERAL");

// dbinterface constructor
configuracion.loadConfig(Constant.ORACLE_BD);
```

---

## 🗄️ Base de Datos

### Conexión a Oracle

#### Parámetros de Conexión
```properties
Host:     172.24.6.105
Puerto:   1398
SID:      UAT
Usuario:  NOVO04005
Password: novo04005  # ⚠️ Sin encriptar
Driver:   oracle.jdbc.driver.OracleDriver
```

#### String de Conexión JDBC
```java
String url = "jdbc:oracle:thin:@172.24.6.105:1398:UAT";
Connection conn = DriverManager.getConnection(url, "NOVO04005", "novo04005");
```

---

### Esquema de Base de Datos

#### Tabla 1: NOVO04005.NOVO_AFILIACION (NA)
**Propósito**: Usuarios afiliados directamente al sistema

| Columna | Tipo | Descripción |
|---------|------|-------------|
| NOTARJETA | VARCHAR | Número de tarjeta (últimos dígitos) |
| IDPERSONA | VARCHAR(18) | CURP del usuario |
| NOMBRE1 | VARCHAR | Primer nombre |
| NOMBRE2 | VARCHAR | Segundo nombre (puede ser NULL) |
| APELLIDO1 | VARCHAR | Apellido paterno |
| APELLIDO2 | VARCHAR | Apellido materno |
| CORREO | VARCHAR | Email |
| TELEFONO2 | VARCHAR | Teléfono celular |
| FECHA_REG | DATE | Fecha de registro |

**Registros típicos**: Usuarios nuevos registrados en el mes actual

---

#### Tabla 2: NOVO04005.NOVO_LOTE_EMI (NLE)
**Propósito**: Usuarios procesados por lotes de emisión

| Columna | Tipo | Descripción |
|---------|------|-------------|
| NRO_TARJETA | VARCHAR | Número de tarjeta (últimos dígitos) |
| ID_EXT_PER | VARCHAR(18) | CURP del usuario |
| NOMBRES | VARCHAR | Primer nombre |
| NOMBRES2 | VARCHAR | Segundo nombre (puede ser NULL) |
| APELLIDOS | VARCHAR | Apellido paterno |
| APELLIDOS2 | VARCHAR | Apellido materno |
| EMAIL | VARCHAR | Correo electrónico |
| TELF_CELULAR | VARCHAR | Teléfono celular |
| DTFECHAPROCESO | DATE | Fecha de procesamiento del lote |

**Registros típicos**: Usuarios de emisiones masivas

---

#### Tabla 3: NOVO04005.MAESTRO_PLASTICO_TEBCA (MPT)
**Propósito**: Maestro de tarjetas plásticas

| Columna | Tipo | Descripción |
|---------|------|-------------|
| NRO_CUENTA | VARCHAR | Número de cuenta completo (tarjeta con prefijo) |
| ID_EXT_EMP | VARCHAR | ID externo de la empresa (Cuenta Principal) |
| CON_ESTATUS | CHAR | Estado de la tarjeta |

**Valores de CON_ESTATUS**:
- `0`: Inactiva (EXCLUIDA del reporte)
- `4`: Cancelada (EXCLUIDA del reporte)
- `9`: Bloqueada (EXCLUIDA del reporte)
- Otros: Activas (INCLUIDAS en el reporte)

**Join**: `SUBSTR(MPT.NRO_CUENTA, 5) = NA.NOTARJETA`
(Remueve prefijo de 4 dígitos para comparar)

---

#### Tabla 4: NOVO04005.MAESTRO_CLIENTES_TEBCA (MCT)
**Propósito**: Maestro de clientes empresariales

| Columna | Tipo | Descripción |
|---------|------|-------------|
| CIRIF_CLIENTE | VARCHAR | RIF/ID del cliente (empresa) |
| NOM_CLIENTE | VARCHAR | Nombre de la empresa |

**Join**: `MCT.CIRIF_CLIENTE = MPT.ID_EXT_EMP`

---

### Query SQL Completa

#### Archivo: constant_queries.properties
**Ubicación**: [config/constant_queries.properties](config/constant_queries.properties)

```sql
SQL_QUERY_GENERAL=\
SELECT \
  MPT.ID_EXT_EMP||'|'||MCT.NOM_CLIENTE||'|'||NA.NOTARJETA||'|'||\
  NA.IDPERSONA||'|'||NA.NOMBRE1||'|'||NA.NOMBRE2||'|'||\
  NA.APELLIDO1||'|'||NA.APELLIDO2||'|'||\
  SUBSTR(NA.IDPERSONA,9,2)||'-'||SUBSTR(NA.IDPERSONA,7,2)||'-'||SUBSTR(NA.IDPERSONA,5,2)||'|'||\
  DECODE(SUBSTR(NA.IDPERSONA,11,1),'H','HOMBRE','M','MUJER')||'|'||\
  UPPER(NA.CORREO)||'|'||NA.TELEFONO2 AS TRAMA \
FROM NOVO04005.NOVO_AFILIACION NA, \
     NOVO04005.MAESTRO_CLIENTES_TEBCA MCT, \
     NOVO04005.MAESTRO_PLASTICO_TEBCA MPT \
WHERE NA.NOTARJETA=SUBSTR(MPT.NRO_CUENTA,5) \
  AND MPT.ID_EXT_EMP=MCT.CIRIF_CLIENTE \
  AND MPT.CON_ESTATUS NOT IN ('0','4','9') \
  AND TO_CHAR(NA.FECHA_REG,'MM/YYYY')=TO_CHAR(SYSDATE,'MM/YYYY') \
\
UNION ALL \
\
SELECT \
  MPT.ID_EXT_EMP||'|'||MCT.NOM_CLIENTE||'|'||NLE.NRO_TARJETA||'|'||\
  NLE.ID_EXT_PER||'|'||NLE.NOMBRES||'|'||NLE.NOMBRES2||'|'||\
  NLE.APELLIDOS||'|'||NLE.APELLIDOS2||'|'||\
  SUBSTR(NLE.ID_EXT_PER,9,2)||'-'||SUBSTR(NLE.ID_EXT_PER,7,2)||'-'||SUBSTR(NLE.ID_EXT_PER,5,2)||'|'||\
  DECODE(SUBSTR(NLE.ID_EXT_PER,11,1),'H','HOMBRE','M','MUJER')||'|'||\
  UPPER(NLE.EMAIL)||'|'||NLE.TELF_CELULAR \
FROM NOVO04005.NOVO_LOTE_EMI NLE, \
     NOVO04005.MAESTRO_CLIENTES_TEBCA MCT, \
     NOVO04005.MAESTRO_PLASTICO_TEBCA MPT \
WHERE NLE.NRO_TARJETA=SUBSTR(MPT.NRO_CUENTA,5) \
  AND MPT.ID_EXT_EMP=MCT.CIRIF_CLIENTE \
  AND MPT.CON_ESTATUS NOT IN ('0','4','9') \
  AND TO_CHAR(NLE.DTFECHAPROCESO,'MM/YYYY')=TO_CHAR(SYSDATE,'MM/YYYY')
```

---

### Análisis de la Query

#### Estructura: UNION ALL de Dos Fuentes

```
┌─────────────────────────────────────┐
│  FUENTE 1: NOVO_AFILIACION         │
│  - Usuarios de afiliación directa   │
│  - Filtro: FECHA_REG del mes actual │
└─────────────────────────────────────┘
              ↓ UNION ALL
┌─────────────────────────────────────┐
│  FUENTE 2: NOVO_LOTE_EMI           │
│  - Usuarios de lotes de emisión     │
│  - Filtro: DTFECHAPROCESO del mes  │
└─────────────────────────────────────┘
```

#### Campos Calculados en el SELECT

**1. Concatenación con Pipe (|)**
```sql
MPT.ID_EXT_EMP||'|'||MCT.NOM_CLIENTE||'|'||NA.NOTARJETA||...
-- Resultado: "123456|EMPRESA SA DE CV|9876543210|..."
```

**2. Extracción de Fecha de Nacimiento del CURP**
```sql
-- CURP formato: AAAA999999HDFLRS99
-- Posiciones:    1234567890123456789
--                     YYMMDD
-- Extracción:
SUBSTR(NA.IDPERSONA,9,2)  -- Día (posiciones 9-10)
||'-'||
SUBSTR(NA.IDPERSONA,7,2)  -- Mes (posiciones 7-8)
||'-'||
SUBSTR(NA.IDPERSONA,5,2)  -- Año (posiciones 5-6)

-- Ejemplo CURP: GARC940615HDFLRN01
-- Extracción: SUBSTR(,9,2)=15, SUBSTR(,7,2)=06, SUBSTR(,5,2)=94
-- Resultado: "15-06-94"
```

**3. Decodificación de Género**
```sql
DECODE(SUBSTR(NA.IDPERSONA,11,1),'H','HOMBRE','M','MUJER')
-- Posición 11 del CURP: H=Hombre, M=Mujer
-- Ejemplo CURP: GARC940615HDFLRN01
--                           ↑ (posición 11)
-- Resultado: "HOMBRE"
```

**4. Normalización de Email**
```sql
UPPER(NA.CORREO)
-- Convierte email a mayúsculas
-- Ejemplo: juan@email.com → JUAN@EMAIL.COM
```

---

#### JOINs y Relaciones

```
NOVO_AFILIACION (NA)
      ↓ NA.NOTARJETA = SUBSTR(MPT.NRO_CUENTA,5)
MAESTRO_PLASTICO_TEBCA (MPT)
      ↓ MPT.ID_EXT_EMP = MCT.CIRIF_CLIENTE
MAESTRO_CLIENTES_TEBCA (MCT)

NOVO_LOTE_EMI (NLE)
      ↓ NLE.NRO_TARJETA = SUBSTR(MPT.NRO_CUENTA,5)
MAESTRO_PLASTICO_TEBCA (MPT)
      ↓ MPT.ID_EXT_EMP = MCT.CIRIF_CLIENTE
MAESTRO_CLIENTES_TEBCA (MCT)
```

#### Filtros Aplicados

**1. Estatus de Tarjeta**
```sql
MPT.CON_ESTATUS NOT IN ('0','4','9')
-- Excluye:
--   0 = Inactivas
--   4 = Canceladas
--   9 = Bloqueadas
```

**2. Rango de Fechas (Mes/Año Actual)**
```sql
-- Para NOVO_AFILIACION:
TO_CHAR(NA.FECHA_REG,'MM/YYYY') = TO_CHAR(SYSDATE,'MM/YYYY')

-- Para NOVO_LOTE_EMI:
TO_CHAR(NLE.DTFECHAPROCESO,'MM/YYYY') = TO_CHAR(SYSDATE,'MM/YYYY')

-- Ejemplo: Si hoy es 23/12/2025
-- Filtra registros con fecha en 12/2025
```

---

### Ejemplo de Resultado de Query

```
Campo 1: 123456
Campo 2: EMPRESA EJEMPLO SA DE CV
Campo 3: 9876543210123456
Campo 4: GARC940615HDFLRN01
Campo 5: CARLOS
Campo 6: ALBERTO
Campo 7: GARCIA
Campo 8: RODRIGUEZ
Campo 9: 15-06-94
Campo 10: HOMBRE
Campo 11: CARLOS.GARCIA@EMAIL.COM
Campo 12: 5551234567

Registro completo (pipe-delimited):
123456|EMPRESA EJEMPLO SA DE CV|9876543210123456|GARC940615HDFLRN01|CARLOS|ALBERTO|GARCIA|RODRIGUEZ|15-06-94|HOMBRE|CARLOS.GARCIA@EMAIL.COM|5551234567
```

---

## 📤 Archivos de Salida

### Archivo de Reporte

#### Nombre del Archivo
```
Patrón:  TDD_Empresarial_{DD}_{MM}_{YYYY}.txt
Ejemplo: TDD_Empresarial_23_12_2025.txt
```

**Generación del nombre**:
```java
// FileProcessor.java
String nombreArchivo = pconfigFile.getProperty("FILE_NAME") +
                       Utils.getDateFile() +
                       pconfigFile.getProperty("FILE_EXT");
// Resultado: "TDD_Empresarial_" + "23_12_2025" + ".txt"
```

#### Ubicación
```
Ruta completa:
c:\Users\cyate\eclipse-workspace\novo_reporte_usuarios_pld\arcrespuesta\TDD_Empresarial_23_12_2025.txt

Ruta configurada en constant_config.properties:
FILE_PATH=arcrespuesta//
```

---

### Formato del Archivo

#### Características Técnicas
```
Delimitador:    | (pipe)
Encoding:       ISO-8859-1 (Latin-1)
Line Ending:    \r\n (CRLF, estilo Windows)
Cabecera:       Siempre presente si hay datos
                Opcional si archivo vacío (configurable)
```

#### Estructura
```
Línea 1:   Cabecera (nombres de columnas)
Líneas 2-N: Datos de usuarios (un usuario por línea)
```

---

### Formato de Cabecera

```
CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|FechaNacimiento|Genero|Email|Telefono
```

**Definición en constant_config.properties**:
```properties
FILED_CABECERA=CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|FechaNacimiento|Genero|Email|Telefono
```

---

### Formato de Datos (12 Campos)

| # | Campo | Tipo | Origen DB | Transformación | Ejemplo |
|---|-------|------|-----------|----------------|---------|
| 1 | CtaPrincipalEmpresarial | Numérico | MPT.ID_EXT_EMP | Ninguna | 123456 |
| 2 | NombreCteCtaEmpresarial | Texto | MCT.NOM_CLIENTE | Ninguna | EMPRESA EJEMPLO SA |
| 3 | Tarjeta | Numérico | NA.NOTARJETA / NLE.NRO_TARJETA | Ninguna | 9876543210123456 |
| 4 | CURP | Alfanumérico(18) | NA.IDPERSONA / NLE.ID_EXT_PER | Ninguna | GARC940615HDFLRN01 |
| 5 | PrimerNombre | Texto | NA.NOMBRE1 / NLE.NOMBRES | Ninguna | CARLOS |
| 6 | SegundoNombre | Texto | NA.NOMBRE2 / NLE.NOMBRES2 | Ninguna | ALBERTO |
| 7 | ApellidoPaterno | Texto | NA.APELLIDO1 / NLE.APELLIDOS | Ninguna | GARCIA |
| 8 | ApellidoMaterno | Texto | NA.APELLIDO2 / NLE.APELLIDOS2 | Ninguna | RODRIGUEZ |
| 9 | FechaNacimiento | Fecha | Extraído de CURP | **dd-MM-yy → dd/MM/yyyy** | 15/06/1994 |
| 10 | Genero | Texto | Extraído de CURP | H→HOMBRE, M→MUJER | HOMBRE |
| 11 | Email | Texto | NA.CORREO / NLE.EMAIL | UPPER() | CARLOS@EMAIL.COM |
| 12 | Telefono | Numérico | NA.TELEFONO2 / NLE.TELF_CELULAR | Ninguna | 5551234567 |

---

### Transformación Crítica: Fecha de Nacimiento

#### Lógica de Transformación
```java
// FileProcessor.java líneas 66-74
String[] info = registro.split("\\|");
String fecha = info[8];  // Posición 8 del array (campo 9)

// Parsing de fecha recibida de BD (formato dd-MM-yy)
Date objSDF = new SimpleDateFormat("dd-MM-yy").parse(fecha);

// Formateo a formato requerido (dd/MM/yyyy)
SimpleDateFormat objSDF2 = new SimpleDateFormat("dd/MM/yyyy");
String fechaTransformada = objSDF2.format(objSDF);

// Reemplazo en el array
info[8] = fechaTransformada;
```

#### Ejemplos de Transformación
```
Input (de BD):    15-06-94      01-01-00      31-12-99
Output (archivo): 15/06/1994    01/01/2000    31/12/1999

⚠️ IMPORTANTE: SimpleDateFormat asume siglo XX (19xx) para años < 30
                                   y siglo XXI (20xx) para años >= 30

Ejemplos:
  29-05-18 → 29/05/2018  (año 18 >= 30? No → siglo XXI)
  94-06-15 → ERROR (día > 31, formato inválido)
```

---

### Ejemplo de Archivo Completo

#### Escenario: 3 Usuarios
```
CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|FechaNacimiento|Genero|Email|Telefono
123456|EMPRESA EJEMPLO SA|9876543210123456|GARC940615HDFLRN01|CARLOS|ALBERTO|GARCIA|RODRIGUEZ|15/06/1994|HOMBRE|CARLOS@EMAIL.COM|5551234567
789012|COMERCIALIZADORA XYZ|1234567890123456|LOPE850320MDFLPR02|MARIA||LOPEZ|PEREZ|20/03/1985|MUJER|MARIA.LOPEZ@MAIL.COM|5559876543
345678|SERVICIOS PROFESIONALES|5555666677778888|MART001105HDFLRT03|JUAN|CARLOS|MARTINEZ||05/11/2000|HOMBRE|JUAN.MARTINEZ@EJEMPLO.COM|5554443322
```

---

### Archivo Vacío (Sin Usuarios)

#### Configuración: CABECERA_VACIO=SI
```
CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|FechaNacimiento|Genero|Email|Telefono
```
**Total de líneas**: 1

#### Configuración: CABECERA_VACIO=NO
```
(archivo completamente vacío, 0 bytes)
```
**Total de líneas**: 0

---

### Archivo de Log

#### Ubicación
```
c:\Users\cyate\eclipse-workspace\novo_reporte_usuarios_pld\log\novo_reporte_usuarios_banorte.log
```

#### Configuración (log4j.properties)
```properties
log4j.rootLogger=DEBUG, FILE, CONSOLE

# File Appender
log4j.appender.FILE=org.apache.log4j.DailyRollingFileAppender
log4j.appender.FILE.File=log/novo_reporte_usuarios_banorte.log
log4j.appender.FILE.DatePattern='.'yyyy-MM-dd
log4j.appender.FILE.layout=org.apache.log4j.PatternLayout
log4j.appender.FILE.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
```

#### Ejemplo de Contenido
```
2025-12-23 14:30:15 INFO  Main:28 - Inicio proceso [version 20200422]
2025-12-23 14:30:15 INFO  Main:30 - Cargando configuracion...
2025-12-23 14:30:16 INFO  dbinterface:145 - Conexion exitosa a Oracle
2025-12-23 14:30:16 DEBUG UsuarioProcessor:38 - Ejecutando query general...
2025-12-23 14:30:17 DEBUG FileProcessor:52 - Cantidad de usuarios consultados: 150
2025-12-23 14:30:17 DEBUG FileProcessor:70 - 123456|EMPRESA EJEMPLO SA|...
2025-12-23 14:30:17 DEBUG FileProcessor:70 - 789012|COMERCIALIZADORA XYZ|...
...
2025-12-23 14:30:18 INFO  Main:98 - Se envio email de notificacion
2025-12-23 14:30:18 INFO  Main:110 - Fin proceso
```

---

## ⚙️ Configuración y Dependencias

### Archivos de Configuración

#### 1. oracle.properties
**Ubicación**: Raíz del proyecto
**Propósito**: Conexión a base de datos Oracle

```properties
bd_host=172.24.6.105
bd_port=1398
bd_name=UAT
bd_user=NOVO04005
bd_password=novo04005
bd_driver=oracle.jdbc.driver.OracleDriver
TypeDrv=2

# Opcional (no usado actualmente):
# encripted=SI
# bd_password=ZW5jcnlwdGVkX19hYmMxMjM=  # Base64 de AES cifrado
```

---

#### 2. constant_config.properties
**Ubicación**: [config/constant_config.properties](config/constant_config.properties)
**Propósito**: Configuración general de la aplicación

```properties
# Formato de archivo
FIELD_SEPARATOR=|
FILE_PATH=arcrespuesta//
FILE_NAME=TDD_Empresarial_
FILE_EXT=.txt
CABECERA_VACIO=SI

# Cabecera del archivo
FILED_CABECERA=CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|FechaNacimiento|Genero|Email|Telefono

# Filtros de fecha
SQL_FEC_ACTUAL=SI
SQL_FEC_ESPECIFICA=NO

# Si SQL_FEC_ESPECIFICA=SI, definir:
# SQL_FEC_ESPECIFICA_DIA_INI=01
# SQL_FEC_ESPECIFICA_MES_INI=12
# SQL_FEC_ESPECIFICA_ANIO_INI=2025
# SQL_FEC_ESPECIFICA_DIA_FIN=31
# SQL_FEC_ESPECIFICA_MES_FIN=12
# SQL_FEC_ESPECIFICA_ANIO_FIN=2025
```

---

#### 3. constant_mail.properties
**Ubicación**: [config/constant_mail.properties](config/constant_mail.properties)
**Propósito**: Configuración de notificaciones por email

```properties
# Servidor SMTP
mail.smtp.host=novopayment-com.mail.protection.outlook.com
mail.smtp.port=25

# Remitente
de=info@novopayment.com

# Autenticación (no utilizada actualmente)
usuario=info@novopayment.onmicrosoft.com
password=Novo.654321

# Destinatarios (separados por ;)
para=operacionesit@novopayment.com

# Asunto
asunto=Detalle de Reporte Usuarios Finales PLD - Banorte

# Control de envío
enviarMail=S
enviarMailBackup=S

# Recursos
IMAGE_PATH=config//logo.png
```

⚠️ **RIESGO DE SEGURIDAD**: Credenciales en texto plano

---

#### 4. constant_queries.properties
**Ubicación**: [config/constant_queries.properties](config/constant_queries.properties)
**Propósito**: Definición de queries SQL

```properties
SQL_QUERY_GENERAL=[Query completa de 26 líneas documentada en sección Base de Datos]
```

---

#### 5. constant_process.properties
**Ubicación**: [config/constant_process.properties](config/constant_process.properties)
**Propósito**: Validaciones de formato (NO IMPLEMENTADAS)

```properties
# Campos GENERAL (21 definiciones)
FIELD_GENERAL_TYPE_0=[1]{1}
FIELD_GENERAL_TYPE_1=[0-9]{10}
FIELD_GENERAL_TYPE_2=[0-9A-Z\s]{1,255}
FIELD_GENERAL_TYPE_3=[0-9]{16}
FIELD_GENERAL_TYPE_4=[0-9]{4}[-]{1}[0-9]{2}[-]{1}[0-9]{2}
...

# Campos DETALLE (14 definiciones)
FIELD_DETALLE_TYPE_0=[2]{1}
FIELD_DETALLE_TYPE_1=[0-9]{10}
...

# Campos CONTROL (5 definiciones)
FIELD_CONTROL_TYPE_0=[9]{1}
FIELD_CONTROL_TYPE_1=[0-9]{10}
...
```

⚠️ **NOTA CRÍTICA**: Estas validaciones están DEFINIDAS pero NO se utilizan en el código actual. No existe código que lea o aplique estas expresiones regulares.

---

#### 6. log4j.properties
**Ubicación**: [src/main/resources/log4j.properties](src/main/resources/log4j.properties)
**Propósito**: Configuración de logging

```properties
log4j.rootLogger=DEBUG, FILE, CONSOLE

# Console Appender
log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender
log4j.appender.CONSOLE.layout=org.apache.log4j.PatternLayout
log4j.appender.CONSOLE.layout.ConversionPattern=%d{HH:mm:ss} %-5p %c{1} - %m%n

# File Appender con rotación diaria
log4j.appender.FILE=org.apache.log4j.DailyRollingFileAppender
log4j.appender.FILE.File=log/novo_reporte_usuarios_banorte.log
log4j.appender.FILE.DatePattern='.'yyyy-MM-dd
log4j.appender.FILE.layout=org.apache.log4j.PatternLayout
log4j.appender.FILE.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n
```

---

### Dependencias Externas (JARs)

#### Ubicación
```
c:\Users\cyate\eclipse-workspace\novo_reporte_usuarios_pld\lib\
```

#### Lista Completa (12 JARs)

| JAR | Tamaño | Propósito | Usado en |
|-----|--------|-----------|----------|
| **activation.jar** | 55 KB | JavaBeans Activation Framework | SendMail (attachments) |
| **mail.jar** | 356 KB | JavaMail API | SendMail.sendEmailHtml() |
| **mailapi.jar** | 193 KB | JavaMail API complementaria | SendMail |
| **commons-logging-1.1.1.jar** | 60 KB | Apache Commons Logging | Todas las clases (log.*) |
| **log4j-1.2.13.jar** | 358 KB | Apache Log4j 1.2 | Sistema de logging |
| **ifxjdbc.jar** | 616 KB | Informix JDBC Driver | **NO USADO** |
| **classes12.jar_off_migracion_bd** | 1.4 MB | Oracle JDBC (deshabilitado) | Backup |
| **devflex-common.jar** | 125 KB | NovoPayment lib | Posibles utilidades |
| **devflex-daemon.jar** | 32 KB | NovoPayment lib | **NO USADO** |
| **NovoDataUtils.jar** | 2 MB | NovoPayment lib | Driver Oracle activo + utils |
| **novo-exception.jar** | 20 KB | Excepciones custom | DatabaseErrorException, etc. |
| **teb-common-be.jar** | 149 KB | Tebca backend lib | Integración Tebca |

---

#### Mapa de Dependencias por Componente

```
Main.java
  ├─> log4j-1.2.13.jar (logging)
  ├─> commons-logging-1.1.1.jar
  └─> NovoDataUtils.jar (dbinterface, Utils)

SendMail.java
  ├─> mail.jar (javax.mail.*)
  ├─> mailapi.jar
  ├─> activation.jar (javax.activation.*)
  └─> log4j-1.2.13.jar

dbinterface.java
  ├─> NovoDataUtils.jar (posiblemente contiene Oracle driver)
  ├─> novo-exception.jar (DatabaseErrorException)
  └─> log4j-1.2.13.jar

FileProcessor.java
  ├─> log4j-1.2.13.jar
  └─> JDK estándar (java.io.*, java.text.*)

UsuarioProcessor.java
  ├─> log4j-1.2.13.jar
  └─> NovoDataUtils.jar (Utils)
```

---

#### Versiones y Antigüedad

| Dependencia | Versión | Fecha Release | Estado |
|-------------|---------|---------------|--------|
| Log4j | 1.2.13 | 2006 | **EOL** - Vulnerabilidades conocidas |
| JavaMail | 1.4.x | ~2005 | Obsoleto, usar Jakarta Mail |
| Commons Logging | 1.1.1 | 2007 | Antiguo pero estable |
| Oracle JDBC | Unknown | ? | Verificar versión exacta |

⚠️ **ADVERTENCIA**: Log4j 1.2.x tiene vulnerabilidades de seguridad. En Go usar alternativa moderna.

---

### Configuración de Build (Gradle)

#### build.gradle
**Ubicación**: [build.gradle](build.gradle)

```gradle
plugins {
    id 'java'
    id 'application'
}

sourceCompatibility = 1.8
targetCompatibility = 1.8

repositories {
    mavenCentral()
}

dependencies {
    implementation fileTree(dir: 'lib', include: ['*.jar'])
}

application {
    mainClass = 'com.novo.main.Main'
}

jar {
    manifest {
        attributes(
            'Main-Class': 'com.novo.main.Main'
        )
    }

    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
```

---

## 🛡️ Controles y Validaciones

### Validaciones Implementadas

#### 1. Validación de Conexión a BD
```java
// Main.java líneas 38-47
if (dbo.rc != 0) {
    log.info("Configuración de BD ORACLE no disponible");
    enviarAlerta(pemail);
    System.exit(0);
}

if (dbo.dbinic() != 0) {
    log.error("Error en conexion al ambiente de datos Oracle");
    enviarAlerta(pemail);
    System.exit(0);
}
```

**Control**: Termina ejecución si no puede conectar a BD
**Notificación**: Envía email de alerta
**Código de salida**: 0 (incorrectamente, debería ser != 0)

---

#### 2. Validación de Ejecución de Query
```java
// UsuarioProcessor.java líneas 42-44
if (db.executeQuery(query) != 0) {
    lista.add("ERROR_BD");
    log.debug("MSG_ERROR_CONSULTA_GENERAL");
}
```

**Control**: Retorna marca de error en lista
**Propagación**: Main verifica si lista contiene "ERROR_BD"
**Notificación**: Email de alerta

---

#### 3. Manejo de Valores NULL en BD
```java
// dbinterface.java líneas 399-401
public String getFieldString(String field) {
    String fieldString = resultado.getString(field);
    if (fieldString == null || fieldString.equalsIgnoreCase("null")) {
        fieldString = "";
    }
    return fieldString;
}
```

**Control**: Convierte NULL a string vacío
**Impacto**: Campos vacíos en archivo aparecen como: `...|campo1||campo3|...`

---

#### 4. Validación de Formato de Fecha
```java
// FileProcessor.java líneas 66-76
try {
    String fecha = info[8];
    Date objSDF = new SimpleDateFormat("dd-MM-yy").parse(fecha);
    SimpleDateFormat objSDF2 = new SimpleDateFormat("dd/MM/yyyy");
    String fechaTransformada = objSDF2.format(objSDF);
    info[8] = fechaTransformada;
} catch (ParseException e) {
    log.error("Error al transformar fecha: " + fecha, e);
    // ⚠️ NO maneja el error, continúa con fecha sin transformar
}
```

**Control**: Intenta parsear fecha
**Problema**: Si falla, continúa sin transformar la fecha
**Riesgo**: Fecha malformada en archivo de salida

---

#### 5. Validación de Estatus de Tarjeta (En Query SQL)
```sql
WHERE MPT.CON_ESTATUS NOT IN ('0','4','9')
```

**Control**: Excluye tarjetas inactivas, canceladas y bloqueadas
**Implementación**: A nivel de BD (en la query)

---

#### 6. Validación de Período (En Query SQL)
```sql
WHERE TO_CHAR(NA.FECHA_REG,'MM/YYYY') = TO_CHAR(SYSDATE,'MM/YYYY')
```

**Control**: Solo registros del mes/año actual
**Implementación**: A nivel de BD (en la query)

---

### Validaciones NO Implementadas (Deadcode)

#### constant_process.properties
Este archivo define 40 expresiones regulares para validar 3 tipos de campos:
- 21 campos GENERAL
- 14 campos DETALLE
- 5 campos CONTROL

**Ejemplo de validaciones definidas**:
```properties
FIELD_GENERAL_TYPE_0=[1]{1}                              # TIPOREG = "1"
FIELD_GENERAL_TYPE_1=[0-9]{10}                           # ID numérico 10 dígitos
FIELD_GENERAL_TYPE_3=[0-9]{16}                           # Tarjeta 16 dígitos
FIELD_GENERAL_TYPE_4=[0-9]{4}[-]{1}[0-9]{2}[-]{1}[0-9]{2}  # Fecha YYYY-MM-DD
FIELD_GENERAL_TYPE_18=.{18}                              # CURP 18 caracteres
```

⚠️ **PROBLEMA CRÍTICO**: No existe código que lea o aplique estas validaciones.

**Código faltante** (no implementado):
```java
// Pseudocódigo de lo que debería existir pero NO existe
Properties validaciones = Utils.getProperties(Constant.CONSTANTS_PROCESS);
Pattern pattern = Pattern.compile(validaciones.getProperty("FIELD_GENERAL_TYPE_0"));
Matcher matcher = pattern.matcher(campo);
if (!matcher.matches()) {
    log.error("Campo inválido");
}
```

---

### Controles de Integridad

#### 1. Encoding del Archivo
```java
new OutputStreamWriter(new FileOutputStream(oFile), "ISO-8859-1")
```

**Control**: Fuerza encoding ISO-8859-1
**Riesgo**: Caracteres fuera de Latin-1 (ñ, á, é, etc.) pueden corromperse
**Necesario para**: Compatibilidad con sistema receptor

---

#### 2. Line Endings (CRLF)
```java
bw.write(cabecera + "\r\n");  // Explícitamente \r\n
```

**Control**: Fuerza line endings Windows (CRLF)
**Necesario para**: Compatibilidad con sistemas Windows

---

#### 3. Separador de Campos
```java
String separador = pconfigFile.getProperty("FIELD_SEPARATOR");  // "|"
```

**Control**: Configurable vía properties
**Actual**: Pipe (|)

---

### Controles de Negocio

#### 1. Exclusión de Duplicados
```sql
-- NO implementada explícitamente
-- Posible duplicación si un usuario existe en ambas tablas:
--   - NOVO_AFILIACION
--   - NOVO_LOTE_EMI
-- El UNION ALL NO elimina duplicados (usar UNION para eso)
```

⚠️ **RIESGO**: Usuario puede aparecer duplicado en el reporte si:
- Fue afiliado directamente (NOVO_AFILIACION)
- Y también procesado en lote (NOVO_LOTE_EMI)
- Ambos en el mismo mes

---

#### 2. Normalización de Email
```sql
UPPER(NA.CORREO)  -- En la query
```

**Control**: Convierte emails a mayúsculas
**Impacto**: juan@email.com → JUAN@EMAIL.COM

---

#### 3. Decodificación de Género
```sql
DECODE(SUBSTR(NA.IDPERSONA,11,1),'H','HOMBRE','M','MUJER')
```

**Control**: Normaliza género a HOMBRE/MUJER
**Problema**: Si CURP tiene otro valor (ej: 'X'), retorna NULL

---

## ⚠️ Riesgos Identificados

### 1. Seguridad

#### 1.1 Credenciales en Texto Plano
**Ubicación**: [oracle.properties](oracle.properties), [constant_mail.properties](config/constant_mail.properties)
**Severidad**: CRÍTICA

```properties
# oracle.properties
bd_password=novo04005

# constant_mail.properties
password=Novo.654321
```

**Impacto**:
- Cualquiera con acceso al repositorio/servidor conoce las credenciales
- Si el repositorio es público, exposición masiva

**Mitigación en Go**:
```go
// Usar variables de entorno
dbPassword := os.Getenv("DB_PASSWORD")
smtpPassword := os.Getenv("SMTP_PASSWORD")

// O usar secretos en Kubernetes/Docker
// O usar servicios de secretos (AWS Secrets Manager, Azure Key Vault)
```

---

#### 1.2 SMTP sin TLS/SSL
**Ubicación**: [SendMail.java](src/main/java/com/novo/main/SendMail.java)
**Severidad**: ALTA

```java
Properties props = new Properties();
props.put("mail.smtp.host", host);
props.put("mail.smtp.port", "25");  // Puerto sin encriptación
// NO establece: mail.smtp.starttls.enable
```

**Impacto**:
- Credenciales de email transmitidas en texto plano
- Contenido de emails interceptable

**Mitigación en Go**:
```go
// Usar TLS
tlsConfig := &tls.Config{
    InsecureSkipVerify: false,
    ServerName:         smtpHost,
}
conn, err := tls.Dial("tcp", smtpHost+":587", tlsConfig)
```

---

#### 1.3 Log4j 1.2.x Vulnerabilidades
**Ubicación**: [lib/log4j-1.2.13.jar](lib/log4j-1.2.13.jar)
**Severidad**: ALTA

**Vulnerabilidades conocidas**:
- CVE-2021-44228 (Log4Shell) - NO aplica a 1.2.x
- CVE-2022-23302, CVE-2022-23305, CVE-2022-23307 - Aplican a 1.2.x

**Mitigación en Go**:
```go
// Usar logger moderno
import "github.com/sirupsen/logrus"

log := logrus.New()
log.SetFormatter(&logrus.JSONFormatter{})
log.SetLevel(logrus.InfoLevel)
```

---

### 2. Estabilidad

#### 2.1 System.exit(0) - Terminación Abrupta
**Ubicación**: [Main.java](src/main/java/com/novo/main/Main.java) líneas 41, 47
**Severidad**: MEDIA

```java
if (dbo.rc != 0) {
    log.info("Configuración de BD ORACLE no disponible");
    enviarAlerta(pemail);
    System.exit(0);  // ⚠️ Termina JVM abruptamente
}
```

**Problemas**:
- No ejecuta bloques `finally`
- No cierra recursos abiertos
- Código de salida 0 indica éxito (debería ser !=0 para error)

**Mitigación en Go**:
```go
func main() {
    if err := run(); err != nil {
        log.Fatalf("Error: %v", err)
        os.Exit(1)
    }
}

func run() error {
    db, err := sql.Open("oracle", connStr)
    if err != nil {
        return fmt.Errorf("conexión BD: %w", err)
    }
    defer db.Close()  // ✅ Siempre se ejecuta

    // ... lógica ...
    return nil
}
```

---

#### 2.2 Sin Connection Pooling
**Ubicación**: [dbinterface.java](src/main/java/com/novo/database/dbinterface.java)
**Severidad**: BAJA (para batch, ALTA para aplicación concurrente)

```java
// Abre conexión
public int dbinic() {
    conexion = DriverManager.getConnection(url, user, pass);
}

// Cierra conexión
public int dbend() {
    conexion.close();
}
```

**Problema**:
- Una nueva conexión por ejecución
- Para batch diario no es crítico
- Si se ejecuta frecuentemente, desperdicia recursos

**Mitigación en Go**:
```go
// sql.DB maneja pooling automáticamente
db, err := sql.Open("oracle", connStr)
db.SetMaxOpenConns(25)
db.SetMaxIdleConns(5)
db.SetConnMaxLifetime(5 * time.Minute)

// db.Query() reutiliza conexiones del pool
```

---

#### 2.3 Manejo Incompleto de Errores de Transformación
**Ubicación**: [FileProcessor.java](src/main/java/com/novo/processor/FileProcessor.java) líneas 66-76
**Severidad**: MEDIA

```java
try {
    Date objSDF = new SimpleDateFormat("dd-MM-yy").parse(fecha);
    String fechaTransformada = objSDF2.format(objSDF);
    info[8] = fechaTransformada;
} catch (ParseException e) {
    log.error("Error al transformar fecha: " + fecha, e);
    // ⚠️ Continúa sin transformar, escribe fecha malformada
}
```

**Impacto**:
- Archivo puede contener fecha en formato incorrecto
- Sistema receptor puede rechazar el archivo

**Mitigación en Go**:
```go
fechaNacimiento, err := time.Parse("02-01-06", fecha)
if err != nil {
    return fmt.Errorf("fecha inválida en registro %d: %w", lineNum, err)
}
fechaFormateada := fechaNacimiento.Format("02/01/2006")
```

---

### 3. Funcionalidad

#### 3.1 Posibles Registros Duplicados
**Ubicación**: [constant_queries.properties](config/constant_queries.properties)
**Severidad**: MEDIA

```sql
SELECT ... FROM NOVO_AFILIACION ...
UNION ALL
SELECT ... FROM NOVO_LOTE_EMI ...
```

**Problema**:
- `UNION ALL` NO elimina duplicados
- Un usuario puede estar en ambas tablas si:
  - Fue afiliado directamente
  - Y procesado en lote
  - Ambos en el mismo mes

**Detección**:
```go
// En Go, validar duplicados por CURP
seen := make(map[string]bool)
for _, record := range records {
    curp := record[3]  // Campo 4: CURP
    if seen[curp] {
        log.Warnf("CURP duplicado: %s", curp)
        continue  // Saltar duplicado
    }
    seen[curp] = true
    // Escribir al archivo
}
```

---

#### 3.2 Validaciones Definidas Pero No Implementadas
**Ubicación**: [constant_process.properties](config/constant_process.properties)
**Severidad**: BAJA (si no son requeridas), ALTA (si son requeridas)

**Problema**:
- 40 expresiones regulares definidas
- Cero líneas de código que las utilicen

**Pregunta crítica para migración**:
- ¿Son requeridas estas validaciones?
- ¿O son legacy code que nunca se usó?

**Recomendación**:
1. Validar con stakeholders si son necesarias
2. Si SÍ: Implementar en Go
3. Si NO: Eliminar de documentación

---

#### 3.3 Encoding ISO-8859-1 - Limitación de Caracteres
**Ubicación**: [FileProcessor.java](src/main/java/com/novo/processor/FileProcessor.java)
**Severidad**: MEDIA

```java
new OutputStreamWriter(new FileOutputStream(oFile), "ISO-8859-1")
```

**Limitación**:
ISO-8859-1 (Latin-1) NO soporta:
- Caracteres fuera de rango 0x00-0xFF
- Algunos símbolos especiales

**Caracteres problemáticos**:
- ✅ Soportados: á, é, í, ó, ú, ñ, Ñ, ü
- ❌ NO soportados: emojis, caracteres asiáticos, símbolos matemáticos

**Mitigación en Go**:
```go
import (
    "golang.org/x/text/encoding/charmap"
    "golang.org/x/text/transform"
)

encoder := charmap.ISO8859_1.NewEncoder()
writer := transform.NewWriter(file, encoder)

// Opcionalmente, reemplazar caracteres no soportados
writer = transform.NewWriter(file,
    charmap.ISO8859_1.NewEncoder().Transformer())
```

---

### 4. Mantenibilidad

#### 4.1 dbinterface Monolítico
**Ubicación**: [dbinterface.java](src/main/java/com/novo/database/dbinterface.java) (736 líneas)
**Severidad**: BAJA

**Problema**:
- Clase muy grande con múltiples responsabilidades
- Mezcla conexión, ejecución, navegación, transformación

**Oportunidad en Go**:
```go
// Separar responsabilidades
package database

// client.go - Gestión de conexión
type Client struct {
    db *sql.DB
}

// query.go - Ejecución de queries
func (c *Client) FetchUsers(ctx context.Context) ([]User, error)

// mapper.go - Transformación de datos
func MapRowToUser(row *sql.Rows) (*User, error)
```

---

#### 4.2 Configuración Dispersa
**Ubicación**: 7 archivos `.properties`
**Severidad**: BAJA

**Problema**:
- Configuración fragmentada en múltiples archivos
- Dificulta cambios y versionado

**Oportunidad en Go**:
```yaml
# config.yaml (unificar toda la configuración)
database:
  host: ${DB_HOST}
  port: ${DB_PORT}
  name: ${DB_NAME}
  user: ${DB_USER}
  password: ${DB_PASSWORD}

output:
  path: ./arcrespuesta
  filename_prefix: TDD_Empresarial_
  encoding: ISO-8859-1
  separator: "|"

email:
  smtp_host: ${SMTP_HOST}
  sender: info@novopayment.com
  recipients:
    - operacionesit@novopayment.com

query:
  sql_file: queries/users.sql
  filter_current_month: true
```

---

### 5. Testing

#### 5.1 Sin Tests Unitarios
**Severidad**: ALTA

**Problema**:
- Cero tests en el proyecto
- Migración sin tests = alto riesgo de regresión

**Recomendación en Go**:
```go
// usuario_processor_test.go
func TestTransformFechaNacimiento(t *testing.T) {
    tests := []struct {
        input    string
        expected string
    }{
        {"15-06-94", "15/06/1994"},
        {"01-01-00", "01/01/2000"},
        {"31-12-99", "31/12/1999"},
    }

    for _, tt := range tests {
        t.Run(tt.input, func(t *testing.T) {
            result := TransformFecha(tt.input)
            if result != tt.expected {
                t.Errorf("got %s, want %s", result, tt.expected)
            }
        })
    }
}
```

---

## 🚀 Plan de Migración a Go

### Estrategia de Migración

#### Enfoque Recomendado: Big Bang con Validación Paralela

```
Fase 1: Desarrollo en Go (paralelo a Java)
  ├─> Implementar funcionalidad completa
  ├─> Escribir tests unitarios y de integración
  └─> Ejecutar ambas versiones en paralelo por 1-2 ciclos

Fase 2: Validación (comparar salidas)
  ├─> Ejecutar Java y Go el mismo día
  ├─> Comparar archivos de salida byte por byte
  └─> Analizar discrepancias

Fase 3: Cutover
  ├─> Desactivar versión Java
  ├─> Activar versión Go como principal
  └─> Mantener Java como fallback por 1 mes
```

---

### Arquitectura Propuesta en Go

#### Estructura de Directorios
```
novo_reporte_usuarios_pld/
├── cmd/
│   └── report/
│       └── main.go                    # Entry point
├── internal/
│   ├── config/
│   │   ├── config.go                 # Carga de configuración
│   │   └── config.yaml               # Archivo de config unificado
│   ├── database/
│   │   ├── client.go                 # Cliente Oracle
│   │   ├── queries.go                # Definición de queries
│   │   └── mapper.go                 # ResultSet → struct
│   ├── processor/
│   │   ├── file_processor.go         # Generación de archivo
│   │   └── transform.go              # Transformaciones de datos
│   ├── notification/
│   │   └── email.go                  # Cliente SMTP
│   └── models/
│       └── usuario.go                # Estructuras de datos
├── pkg/
│   └── logger/
│       └── logger.go                 # Configuración de logging
├── queries/
│   └── users.sql                     # Query SQL externalizada
├── config/
│   ├── config.yaml                   # Configuración principal
│   └── logo.png                      # Recursos
├── arcrespuesta/                     # Archivos de salida
├── logs/                             # Logs
├── go.mod
├── go.sum
├── Makefile
├── Dockerfile
└── README.md
```

---

### Stack Tecnológico en Go

#### Dependencias Principales
```go
// go.mod
module github.com/novopayment/novo-reporte-usuarios-pld

go 1.22

require (
    github.com/sijms/go-ora/v2 v2.8.10          // Driver Oracle (pure Go)
    github.com/spf13/viper v1.18.2              // Configuración (YAML/ENV)
    github.com/sirupsen/logrus v1.9.3           // Structured logging
    gopkg.in/gomail.v2 v2.0.0-20160411212932-81ebce5c23df  // Email con HTML
    golang.org/x/text v0.14.0                   // Encoding ISO-8859-1
)
```

---

### Mapeo de Componentes Java → Go

| Componente Java | Componente Go | Responsabilidad |
|-----------------|---------------|-----------------|
| Main.java | cmd/report/main.go | Entry point, orquestación |
| dbinterface.java | internal/database/client.go | Cliente BD |
| dbconfig.java | internal/config/config.go | Configuración |
| UsuarioProcessor.java | internal/database/queries.go | Queries |
| FileProcessor.java | internal/processor/file_processor.go | Generación archivo |
| SendMail.java | internal/notification/email.go | SMTP |
| Utils.java | internal/processor/transform.go | Transformaciones |
| Constant.java | internal/config/constants.go | Constantes |

---

### Ejemplo de Código Go

#### 1. main.go (Entry Point)
```go
package main

import (
    "context"
    "fmt"
    "os"
    "time"

    "github.com/novopayment/novo-reporte-usuarios-pld/internal/config"
    "github.com/novopayment/novo-reporte-usuarios-pld/internal/database"
    "github.com/novopayment/novo-reporte-usuarios-pld/internal/notification"
    "github.com/novopayment/novo-reporte-usuarios-pld/internal/processor"
    "github.com/sirupsen/logrus"
)

func main() {
    // Configurar logger
    log := logrus.New()
    log.SetFormatter(&logrus.JSONFormatter{})
    log.SetLevel(logrus.InfoLevel)

    // Ejecutar proceso principal
    if err := run(log); err != nil {
        log.Fatalf("Error en ejecución: %v", err)
        os.Exit(1)
    }
}

func run(log *logrus.Logger) error {
    ctx := context.Background()

    // 1. Cargar configuración
    cfg, err := config.Load()
    if err != nil {
        return fmt.Errorf("cargar config: %w", err)
    }

    // 2. Conectar a BD
    dbClient, err := database.NewClient(cfg.Database)
    if err != nil {
        notification.SendAlert(cfg.Email, "BD no disponible")
        return fmt.Errorf("conectar BD: %w", err)
    }
    defer dbClient.Close()

    // 3. Consultar usuarios
    usuarios, err := dbClient.FetchUsuariosPLD(ctx, time.Now())
    if err != nil {
        notification.SendAlert(cfg.Email, "Error en query")
        return fmt.Errorf("consultar usuarios: %w", err)
    }

    // 4. Generar archivo
    filePath, err := processor.GenerarArchivo(cfg.Output, usuarios)
    if err != nil {
        return fmt.Errorf("generar archivo: %w", err)
    }

    // 5. Enviar notificación
    err = notification.SendSuccess(cfg.Email, len(usuarios), filePath)
    if err != nil {
        log.Warnf("Error al enviar email: %v", err)
    }

    log.Infof("Proceso completado. Usuarios: %d, Archivo: %s", len(usuarios), filePath)
    return nil
}
```

---

#### 2. database/client.go
```go
package database

import (
    "context"
    "database/sql"
    "fmt"
    "time"

    _ "github.com/sijms/go-ora/v2"
)

type Client struct {
    db *sql.DB
}

func NewClient(cfg DatabaseConfig) (*Client, error) {
    // Construir DSN para Oracle
    dsn := fmt.Sprintf(
        "oracle://%s:%s@%s:%d/%s",
        cfg.User,
        cfg.Password,
        cfg.Host,
        cfg.Port,
        cfg.Name,
    )

    db, err := sql.Open("oracle", dsn)
    if err != nil {
        return nil, fmt.Errorf("open: %w", err)
    }

    // Configurar connection pool
    db.SetMaxOpenConns(25)
    db.SetMaxIdleConns(5)
    db.SetConnMaxLifetime(5 * time.Minute)

    // Verificar conexión
    if err := db.Ping(); err != nil {
        return nil, fmt.Errorf("ping: %w", err)
    }

    return &Client{db: db}, nil
}

func (c *Client) Close() error {
    return c.db.Close()
}

func (c *Client) FetchUsuariosPLD(ctx context.Context, fecha time.Time) ([]Usuario, error) {
    query := GetQueryUsuariosPLD()  // Leer de archivo SQL

    rows, err := c.db.QueryContext(ctx, query)
    if err != nil {
        return nil, fmt.Errorf("query: %w", err)
    }
    defer rows.Close()

    var usuarios []Usuario
    for rows.Next() {
        var u Usuario
        if err := rows.Scan(
            &u.CtaPrincipal,
            &u.NombreCliente,
            &u.Tarjeta,
            &u.CURP,
            &u.PrimerNombre,
            &u.SegundoNombre,
            &u.ApellidoPaterno,
            &u.ApellidoMaterno,
            &u.FechaNacimiento,  // dd-MM-yy
            &u.Genero,
            &u.Email,
            &u.Telefono,
        ); err != nil {
            return nil, fmt.Errorf("scan: %w", err)
        }
        usuarios = append(usuarios, u)
    }

    if err := rows.Err(); err != nil {
        return nil, fmt.Errorf("rows: %w", err)
    }

    return usuarios, nil
}
```

---

#### 3. processor/file_processor.go
```go
package processor

import (
    "bufio"
    "fmt"
    "os"
    "strings"
    "time"

    "golang.org/x/text/encoding/charmap"
    "golang.org/x/text/transform"
)

func GenerarArchivo(cfg OutputConfig, usuarios []Usuario) (string, error) {
    // Generar nombre de archivo
    fecha := time.Now().Format("02_01_2006")
    filename := fmt.Sprintf("%s%s%s", cfg.Path, cfg.FilenamePrefix, fecha+".txt")

    // Crear archivo con encoding ISO-8859-1
    file, err := os.Create(filename)
    if err != nil {
        return "", fmt.Errorf("crear archivo: %w", err)
    }
    defer file.Close()

    // Configurar encoder ISO-8859-1
    encoder := charmap.ISO8859_1.NewEncoder()
    writer := bufio.NewWriter(transform.NewWriter(file, encoder))
    defer writer.Flush()

    // Escribir cabecera
    cabecera := "CtaPrincipalEmpresarial|NombreCteCtaEmpresarial|Tarjeta|CURP|" +
        "PrimerNombre|SegundoNombre|ApellidoPaterno|ApellidoMaterno|" +
        "FechaNacimiento|Genero|Email|Telefono"
    if _, err := writer.WriteString(cabecera + "\r\n"); err != nil {
        return "", fmt.Errorf("escribir cabecera: %w", err)
    }

    // Escribir datos
    for i, u := range usuarios {
        // Transformar fecha: dd-MM-yy → dd/MM/yyyy
        fechaTransformada, err := TransformFecha(u.FechaNacimiento)
        if err != nil {
            return "", fmt.Errorf("transformar fecha línea %d: %w", i+2, err)
        }

        linea := fmt.Sprintf("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n",
            u.CtaPrincipal,
            u.NombreCliente,
            u.Tarjeta,
            u.CURP,
            u.PrimerNombre,
            u.SegundoNombre,
            u.ApellidoPaterno,
            u.ApellidoMaterno,
            fechaTransformada,
            u.Genero,
            u.Email,
            u.Telefono,
        )

        if _, err := writer.WriteString(linea); err != nil {
            return "", fmt.Errorf("escribir línea %d: %w", i+2, err)
        }
    }

    return filename, nil
}

func TransformFecha(fechaInput string) (string, error) {
    // Parsear dd-MM-yy
    t, err := time.Parse("02-01-06", fechaInput)
    if err != nil {
        return "", fmt.Errorf("parsear fecha '%s': %w", fechaInput, err)
    }

    // Formatear a dd/MM/yyyy
    return t.Format("02/01/2006"), nil
}
```

---

#### 4. notification/email.go
```go
package notification

import (
    "fmt"
    "time"

    "gopkg.in/gomail.v2"
)

func SendSuccess(cfg EmailConfig, cantidadUsuarios int, archivo string) error {
    m := gomail.NewMessage()
    m.SetHeader("From", cfg.Sender)
    m.SetHeader("To", cfg.Recipients...)
    m.SetHeader("Subject", "Detalle de Reporte Usuarios Finales PLD - Banorte")

    body := fmt.Sprintf(`
        <html>
        <body style="font-family: Arial; font-size: 12px;">
            <p><img src="cid:logo" width="150"/></p>
            <p><b>Fecha/hora:</b> %s</p>
            <p>Se notifica el resumen del proceso:</p>
            <p>Nro de usuarios enviados en el archivo: %d</p>
            <p>Cordialmente,<br/>Equipo Técnico</p>
        </body>
        </html>
    `, time.Now().Format("02/01/2006 15:04:05"), cantidadUsuarios)

    m.SetBody("text/html", body)
    m.Embed(cfg.LogoPath)

    d := gomail.NewDialer(cfg.SMTPHost, cfg.SMTPPort, "", "")

    if err := d.DialAndSend(m); err != nil {
        return fmt.Errorf("enviar email: %w", err)
    }

    return nil
}

func SendAlert(cfg EmailConfig, mensaje string) error {
    m := gomail.NewMessage()
    m.SetHeader("From", cfg.Sender)
    m.SetHeader("To", cfg.Recipients...)
    m.SetHeader("Subject", "Banorte / Alerta para Reporte Usuarios Finales PLD")

    body := fmt.Sprintf(`
        <html>
        <body style="font-family: Arial; font-size: 12px;">
            <p><img src="cid:logo" width="150"/></p>
            <p><b>Fecha/hora:</b> %s</p>
            <p>Se notifica que se presentaron fallas en el proceso correspondiente.</p>
            <p><b>Observaciones:</b></p>
            <table border="1">
                <tr><th>Detalle</th></tr>
                <tr><td>%s</td></tr>
            </table>
        </body>
        </html>
    `, time.Now().Format("02/01/2006 15:04:05"), mensaje)

    m.SetBody("text/html", body)
    m.Embed(cfg.LogoPath)

    d := gomail.NewDialer(cfg.SMTPHost, cfg.SMTPPort, "", "")

    if err := d.DialAndSend(m); err != nil {
        return fmt.Errorf("enviar alerta: %w", err)
    }

    return nil
}
```

---

### Mejoras sobre el Original

#### 1. Manejo de Configuración
```go
// ✅ Variables de entorno con fallback a YAML
type Config struct {
    Database DatabaseConfig `mapstructure:"database"`
    Output   OutputConfig   `mapstructure:"output"`
    Email    EmailConfig    `mapstructure:"email"`
}

func Load() (*Config, error) {
    viper.SetConfigName("config")
    viper.AddConfigPath("./config")
    viper.AutomaticEnv()  // Lee variables de entorno

    // Mapeo de env vars
    viper.BindEnv("database.host", "DB_HOST")
    viper.BindEnv("database.password", "DB_PASSWORD")
    // ...
}
```

#### 2. Contexts y Timeouts
```go
// ✅ Control de tiempo de ejecución
ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
defer cancel()

rows, err := db.QueryContext(ctx, query)  // Se cancela si excede timeout
```

#### 3. Structured Logging
```go
// ✅ Logs estructurados en JSON
log.WithFields(logrus.Fields{
    "usuarios":  len(usuarios),
    "archivo":   filePath,
    "duracion":  time.Since(startTime),
}).Info("Proceso completado")

// Output:
// {"level":"info","msg":"Proceso completado","usuarios":150,"archivo":"...","duracion":"2.3s","time":"2025-12-23T14:30:18Z"}
```

#### 4. Graceful Shutdown
```go
// ✅ Manejo de señales del sistema
func main() {
    sigChan := make(chan os.Signal, 1)
    signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

    go func() {
        <-sigChan
        log.Info("Señal de terminación recibida, cerrando...")
        cancel()  // Cancela contexto
    }()

    run(ctx, log)
}
```

#### 5. Testing
```go
// ✅ Tests unitarios
func TestTransformFecha(t *testing.T) {
    tests := []struct {
        name     string
        input    string
        expected string
        wantErr  bool
    }{
        {"Fecha válida 1994", "15-06-94", "15/06/1994", false},
        {"Fecha válida 2000", "01-01-00", "01/01/2000", false},
        {"Fecha inválida", "99-99-99", "", true},
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            result, err := TransformFecha(tt.input)
            if (err != nil) != tt.wantErr {
                t.Errorf("error = %v, wantErr %v", err, tt.wantErr)
            }
            if result != tt.expected {
                t.Errorf("got %s, want %s", result, tt.expected)
            }
        })
    }
}
```

---

## ✅ Tareas de Migración

### Fase 1: Preparación

#### Tarea 1.1: Setup del Proyecto Go
- [ ] Crear repositorio Go
- [ ] Inicializar go.mod
- [ ] Configurar estructura de directorios
- [ ] Configurar Makefile
- [ ] Configurar CI/CD

#### Tarea 1.2: Configuración
- [ ] Crear config.yaml unificado
- [ ] Implementar carga de configuración con Viper
- [ ] Migrar todas las properties a YAML
- [ ] Configurar variables de entorno
- [ ] Documentar configuración

#### Tarea 1.3: Setup de Base de Datos
- [ ] Instalar driver go-ora
- [ ] Implementar database/client.go
- [ ] Configurar connection pool
- [ ] Externalizar query SQL a archivo .sql
- [ ] Implementar FetchUsuariosPLD()

---

### Fase 2: Implementación Core

#### Tarea 2.1: Modelos de Datos
- [ ] Definir struct Usuario
- [ ] Implementar métodos de serialización
- [ ] Validación de campos

#### Tarea 2.2: Processor de Archivos
- [ ] Implementar GenerarArchivo()
- [ ] Configurar encoding ISO-8859-1
- [ ] Implementar TransformFecha()
- [ ] Forzar line endings CRLF
- [ ] Manejo de archivos vacíos

#### Tarea 2.3: Notificaciones
- [ ] Implementar cliente SMTP con gomail
- [ ] Implementar SendSuccess()
- [ ] Implementar SendAlert()
- [ ] Embebir logo en HTML
- [ ] Configurar TLS (mejora sobre original)

---

### Fase 3: Testing

#### Tarea 3.1: Tests Unitarios
- [ ] Tests para TransformFecha()
- [ ] Tests para GenerarArchivo() (mocks)
- [ ] Tests para parsing de configuración
- [ ] Tests para formateo de email

#### Tarea 3.2: Tests de Integración
- [ ] Test de conexión a BD (ambiente test)
- [ ] Test end-to-end con BD de prueba
- [ ] Validación de formato de archivo

#### Tarea 3.3: Tests de Regresión
- [ ] Comparar salida Go vs Java (mismo input)
- [ ] Validar encoding byte por byte
- [ ] Validar conteo de usuarios
- [ ] Validar formato de email

---

### Fase 4: Mejoras de Seguridad

#### Tarea 4.1: Credenciales
- [ ] Eliminar credenciales de archivos de config
- [ ] Configurar lectura desde variables de entorno
- [ ] Documentar gestión de secretos
- [ ] Implementar rotación de credenciales (opcional)

#### Tarea 4.2: SMTP Seguro
- [ ] Implementar TLS para SMTP
- [ ] Configurar autenticación moderna
- [ ] Validar certificados

#### Tarea 4.3: Logging Seguro
- [ ] Evitar logging de datos sensibles
- [ ] Implementar niveles de log apropiados
- [ ] Configurar rotación de logs

---

### Fase 5: Deployment

#### Tarea 5.1: Containerización
- [ ] Crear Dockerfile
- [ ] Crear docker-compose.yml para testing
- [ ] Configurar multi-stage build
- [ ] Optimizar tamaño de imagen

#### Tarea 5.2: Documentación
- [ ] README.md con instrucciones de uso
- [ ] Documentar variables de entorno
- [ ] Documentar proceso de deployment
- [ ] Crear runbook de operaciones

#### Tarea 5.3: Ejecución Paralela
- [ ] Configurar cron/scheduler para ambas versiones
- [ ] Implementar script de comparación de salidas
- [ ] Monitorear discrepancias por 2 ciclos

---

### Fase 6: Cutover

#### Tarea 6.1: Validación Final
- [ ] Revisión de código por equipo
- [ ] Ejecución de todos los tests
- [ ] Validación de configuración productiva
- [ ] Aprobación de stakeholders

#### Tarea 6.2: Despliegue
- [ ] Desactivar job Java en scheduler
- [ ] Activar job Go en scheduler
- [ ] Monitorear primera ejecución
- [ ] Validar archivo generado
- [ ] Validar email enviado

#### Tarea 6.3: Post-Migración
- [ ] Mantener versión Java como fallback (1 mes)
- [ ] Documentar lecciones aprendidas
- [ ] Archivar código Java
- [ ] Actualizar documentación de sistemas

---

## 📊 Checklist de Validación

### Funcionalidad
- [ ] Genera archivo con nombre correcto (TDD_Empresarial_DD_MM_YYYY.txt)
- [ ] Archivo tiene encoding ISO-8859-1
- [ ] Archivo usa CRLF como line ending
- [ ] Cabecera tiene 12 campos pipe-delimited
- [ ] Datos tienen 12 campos pipe-delimited
- [ ] Fecha transformada de dd-MM-yy a dd/MM/yyyy
- [ ] Género transformado de H/M a HOMBRE/MUJER
- [ ] Email en mayúsculas
- [ ] Conteo de usuarios correcto
- [ ] Email de éxito enviado correctamente
- [ ] Email de alerta enviado en caso de error
- [ ] Logo embebido en email

### Rendimiento
- [ ] Ejecución completa en < 5 minutos
- [ ] Uso de memoria < 512 MB
- [ ] Connection pool funciona correctamente

### Seguridad
- [ ] No hay credenciales hardcodeadas
- [ ] SMTP usa TLS
- [ ] Logs no contienen datos sensibles

### Observabilidad
- [ ] Logs estructurados en JSON
- [ ] Métricas de ejecución registradas
- [ ] Errores con contexto suficiente

---

## 📚 Referencias

### Documentación de Componentes Clave

#### Archivos Java de Referencia
1. [Main.java](src/main/java/com/novo/main/Main.java) - Entry point y orquestación
2. [dbinterface.java](src/main/java/com/novo/database/dbinterface.java) - Cliente JDBC
3. [FileProcessor.java](src/main/java/com/novo/processor/FileProcessor.java) - Generación de archivo
4. [UsuarioProcessor.java](src/main/java/com/novo/processor/UsuarioProcessor.java) - Query de usuarios
5. [SendMail.java](src/main/java/com/novo/main/SendMail.java) - Cliente SMTP

#### Archivos de Configuración
1. [oracle.properties](oracle.properties) - Conexión BD
2. [constant_config.properties](config/constant_config.properties) - Config general
3. [constant_queries.properties](config/constant_queries.properties) - Queries SQL
4. [constant_mail.properties](config/constant_mail.properties) - Config SMTP
5. [log4j.properties](src/main/resources/log4j.properties) - Logging

### Librerías Go Recomendadas
- **go-ora**: https://github.com/sijms/go-ora
- **Viper**: https://github.com/spf13/viper
- **Logrus**: https://github.com/sirupsen/logrus
- **Gomail**: https://github.com/go-gomail/gomail

### Recursos Adicionales
- **Oracle SQL Developer**: Para testing de queries
- **Go by Example**: https://gobyexample.com/
- **Effective Go**: https://golang.org/doc/effective_go

---

## 🔍 Preguntas Pendientes para Stakeholders

1. **Validaciones**: ¿Son necesarias las validaciones definidas en `constant_process.properties`?
2. **Duplicados**: ¿Es aceptable que un usuario aparezca duplicado si está en ambas tablas?
3. **SMTP TLS**: ¿Podemos migrar a SMTP con TLS/puerto 587?
4. **Encoding**: ¿Es estrictamente necesario ISO-8859-1 o podemos usar UTF-8?
5. **Frecuencia**: ¿Cuál es la frecuencia de ejecución actual? (diaria/mensual)
6. **Volumen**: ¿Cuántos usuarios típicamente procesa en cada ejecución?
7. **SLA**: ¿Cuál es el SLA de generación del reporte?
8. **Destinatarios**: ¿Los destinatarios de email son correctos y actuales?
9. **Fallback**: ¿Qué hacer si falla la generación del archivo?
10. **Retries**: ¿Implementar reintentos automáticos o notificar y terminar?

---

## 📝 Notas Finales

Este documento proporciona una visión completa del sistema actual en Java para facilitar la migración a Go. Se recomienda:

1. **Revisar con el equipo** todas las secciones de riesgos
2. **Validar stakeholders** las preguntas pendientes
3. **Priorizar seguridad** en la implementación Go
4. **Implementar tests** desde el inicio
5. **Ejecutar en paralelo** antes del cutover definitivo

**Última actualización**: 23/12/2025
**Autor**: Análisis automatizado del proyecto Java
**Próximos pasos**: Iniciar Fase 1 de migración

---

## 🏁 Resumen Ejecutivo de Migración

### ¿Por qué migrar a Go?

**Ventajas**:
- Deployment simplificado (binario único vs JVM + JARs)
- Menor uso de memoria
- Startup time más rápido
- Dependencias modernas y mantenidas
- Mejor seguridad por defecto
- Tooling moderno (testing, profiling, etc.)

**Desventajas**:
- Requiere reescritura completa
- Team learning curve en Go
- Riesgo de introducir bugs en migración

### Esfuerzo Estimado

**Complejidad**: Media
**Componentes**: 8 clases Java → 6 paquetes Go
**Tests**: Crear desde cero (alto valor)

### Riesgo

**Nivel**: Bajo-Medio

**Mitigadores**:
- Funcionalidad bien definida
- Tests de regresión con salidas Java
- Ejecución paralela
- Rollback plan (mantener Java 1 mes)

### Go/No-Go Decision

**Proceder con migración SI**:
- Team tiene capacidad de Go
- Hay tiempo para testing exhaustivo
- Stakeholders aprueban riesgos
- Hay plan de rollback

**NO proceder SI**:
- Sistema Java cumple requisitos actuales
- No hay expertise en Go en el team
- Presión de tiempo alta
- Sin aprobación de stakeholders

---

**FIN DEL DOCUMENTO**
