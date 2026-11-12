# Integración de ADBUtility Guard con ADB UtilityTools (Electron Desktop)

Esta guía técnica describe cómo conectar **ADB UtilityTools** (aplicación de escritorio desarrollada en Electron / NodeJS) con **ADBUtility Guard** instalada en el dispositivo Android mediante el protocolo ADB.

---

## 1. Principio de Seguridad

1. **Solo Lectura Estricta**: La aplicación Android expone consultas de estado e información de versiones. No recibe ni ejecuta comandos arbitrarios desde Electron.
2. **Sin permisos especiales requeridos**: No requiere Root, Shizuku ni Device Owner en el teléfono.
3. **Formato JSON Nativo**: Todas las respuestas devuelven cadenas JSON estandarizadas listas para ser parseadas por `JSON.parse()`.

---

## 2. Métodos de Comunicación Soportados

Existen dos vías de comunicación disponibles:

| Método | Comando ADB | Eficiencia | Caso de Uso Recomendado |
|---|---|---|---|
| **ContentProvider (`content call`)** | `adb shell content call --uri content://com.adbutility.guard.provider --method <method>` | **Muy Alta** (cero retardo, síncrono) | Comunicación habitual desde Electron |
| **Broadcast (`am broadcast`)** | `adb shell am broadcast -a com.adbutility.guard.QUERY --es action <action>` | Alta | Alternativa por línea de comandos |

---

## 3. Especificación de Comandos y Respuestas

### A. Verificar Instalación y Versión
- **Comando**:
  ```bash
  adb shell content call --uri content://com.adbutility.guard.provider --method getVersion
  ```
- **Salida estándar (stdout)**:
  ```text
  Result: Bundle[{response_json={"packageName":"com.adbutility.guard","versionName":"1.0.0","versionCode":100,"isInstalled":true}}]
  ```

### B. Consultar Estado del Servicio y Dispositivo
- **Comando**:
  ```bash
  adb shell content call --uri content://com.adbutility.guard.provider --method getStatus
  ```
- **Respuesta JSON**:
  ```json
  {
    "serviceEnabled": true,
    "protectionEnabled": true,
    "cooldownSeconds": 5,
    "totalDetections": 3,
    "deviceInfo": {
      "manufacturer": "Samsung",
      "brand": "samsung",
      "model": "SM-S918B",
      "device": "dm3q",
      "androidVersion": "14",
      "sdkInt": 34,
      "securityPatch": "2026-10-01",
      "oemType": "SAMSUNG"
    }
  }
  ```

### C. Consultar Última Detección
- **Comando**:
  ```bash
  adb shell content call --uri content://com.adbutility.guard.provider --method getLastDetection
  ```
- **Respuesta JSON**:
  ```json
  {
    "hasDetections": true,
    "event": {
      "id": 1,
      "timestamp": 1729002345000,
      "formattedDate": "2026-11-09 09:15:20",
      "packageName": "com.android.settings",
      "detectionType": "keyword: restablecer datos de fábrica",
      "actionTaken": "CANCELAR"
    }
  }
  ```

### D. Consultar Historial de Eventos
- **Comando**:
  ```bash
  adb shell content call --uri content://com.adbutility.guard.provider --method getHistory --arg 50
  ```
- **Respuesta JSON**:
  ```json
  {
    "count": 2,
    "totalInDb": 2,
    "events": [
      {
        "id": 2,
        "timestamp": 1729002400000,
        "formattedDate": "2026-11-09 09:16:40",
        "packageName": "com.android.settings",
        "detectionType": "keyword: erase all data",
        "actionTaken": "CANCELAR"
      },
      {
        "id": 1,
        "timestamp": 1729002345000,
        "formattedDate": "2026-11-09 09:15:20",
        "packageName": "com.android.settings",
        "detectionType": "keyword: restablecer datos de fábrica",
        "actionTaken": "CANCELAR"
      }
    ]
  }
  ```

### E. Limpiar Historial
- **Comando**:
  ```bash
  adb shell content call --uri content://com.adbutility.guard.provider --method clearHistory
  ```
- **Respuesta JSON**:
  ```json
  {
    "success": true,
    "message": "History cleared successfully"
  }
  ```

---

## 4. Implementación en Electron / TypeScript

A continuación se presenta un módulo de cliente TypeScript reutilizable para ADB UtilityTools:

```typescript
import { execFile } from 'child_process';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);

export interface GuardStatus {
  serviceEnabled: boolean;
  protectionEnabled: boolean;
  cooldownSeconds: number;
  totalDetections: number;
  deviceInfo: {
    manufacturer: string;
    brand: string;
    model: string;
    androidVersion: string;
    sdkInt: number;
    oemType: string;
  };
}

export interface DetectionEvent {
  id: number;
  timestamp: number;
  formattedDate: string;
  packageName: string;
  detectionType: string;
  actionTaken: string;
}

export class AdbGuardClient {
  private adbPath: string;
  private deviceSerial?: string;

  constructor(adbPath = 'adb', deviceSerial?: string) {
    this.adbPath = adbPath;
    this.deviceSerial = deviceSerial;
  }

  private async executeAdb(args: string[]): Promise<string> {
    const fullArgs = this.deviceSerial ? ['-s', this.deviceSerial, ...args] : args;
    const { stdout } = await execFileAsync(this.adbPath, fullArgs, { encoding: 'utf-8', timeout: 8000 });
    return stdout;
  }

  /**
   * Ejecuta un 'content call' en ADBUtility Guard y extrae el JSON retornado.
   */
  private async callProvider(method: string, arg?: string): Promise<any> {
    const args = [
      'shell',
      'content',
      'call',
      '--uri',
      'content://com.adbutility.guard.provider',
      '--method',
      method
    ];
    if (arg) {
      args.push('--arg', arg);
    }

    const output = await this.executeAdb(args);
    // Extraer la cadena de response_json usando expresión regular
    const match = output.match(/response_json=({.*?}|\[.*?\])/);
    if (!match) {
      throw new Error(`Respuesta inválida de ADBUtility Guard: ${output}`);
    }

    return JSON.parse(match[1]);
  }

  /**
   * Comprueba si ADBUtility Guard está instalada.
   */
  async isInstalled(): Promise<boolean> {
    try {
      const result = await this.callProvider('getVersion');
      return result.isInstalled === true;
    } catch {
      return false;
    }
  }

  /**
   * Obtiene el estado del servicio y la protección.
   */
  async getStatus(): Promise<GuardStatus> {
    return this.callProvider('getStatus');
  }

  /**
   * Obtiene la última detección registrada.
   */
  async getLastDetection(): Promise<{ hasDetections: boolean; event?: DetectionEvent }> {
    return this.callProvider('getLastDetection');
  }

  /**
   * Obtiene el historial de detecciones.
   */
  async getHistory(limit = 50): Promise<{ count: number; totalInDb: number; events: DetectionEvent[] }> {
    return this.callProvider('getHistory', limit.toString());
  }

  /**
   * Limpia el historial de eventos en el teléfono.
   */
  async clearHistory(): Promise<boolean> {
    const res = await this.callProvider('clearHistory');
    return res.success === true;
  }

  /**
   * Activa el servicio de accesibilidad directamente vía ADB (sin interacción manual).
   */
  async enableAccessibilityService(): Promise<void> {
    const serviceComponent = 'com.adbutility.guard/com.adbutility.guard.ResetAccessibilityService';
    await this.executeAdb([
      'shell',
      'settings',
      'put',
      'secure',
      'enabled_accessibility_services',
      serviceComponent
    ]);
    await this.executeAdb(['shell', 'settings', 'put', 'secure', 'accessibility_enabled', '1']);
  }
}
```

