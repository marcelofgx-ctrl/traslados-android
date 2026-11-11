#!/usr/bin/env python3
"""Offline, read-only validation of Mapa Trayectos' Supabase history ZIP.

No extraction, networking, DB writes, secrets or individual passenger rows printed.
"""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import BadZipFile, ZipFile

DATASETS = (
    'jornadas', 'viajes_mapa', 'puntos_gps', 'reservas',
    'eventos_reserva', 'paradas_reserva', 'mensajes_reserva',
)
FORBIDDEN_KEYS = {
    'public_token', 'device_secret', 'password', 'password_hash',
    'pin', 'pin_hash', 'access_token', 'refresh_token', 'session_token',
    'api_key', 'secret_key',
}


def verify(path: Path):
    if not path.is_file():
        raise ValueError('El archivo ZIP no existe')
    expected_names = {'manifest.json'} | {f'historial/{name}.jsonl' for name in DATASETS}
    with ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != expected_names:
            raise ValueError('El ZIP tiene entradas duplicadas, faltantes o inesperadas')
        with archive.open('manifest.json') as reader:
            manifest = json.load(reader)
        if manifest.get('format') != 'traslados-cloud-history-export' or manifest.get('format_version') != 1:
            raise ValueError('Formato o versión de manifest no reconocido')
        if not isinstance(manifest.get('cutoff_ms'), int) or manifest['cutoff_ms'] <= 0:
            raise ValueError('Corte temporal inválido')
        expected = manifest.get('counts')
        if not isinstance(expected, dict) or set(expected) != set(DATASETS):
            raise ValueError('Manifest con conteos incompletos')
        observed = {}
        for name in DATASETS:
            count = 0
            with archive.open(f'historial/{name}.jsonl') as stream:
                for raw_line in stream:
                    if not raw_line.endswith(b'\n'):
                        raise ValueError(f'{name}: línea no terminada correctamente')
                    record = json.loads(raw_line)
                    if not isinstance(record, dict):
                        raise ValueError(f'{name}: el registro no es un objeto JSON')
                    secrets = FORBIDDEN_KEYS.intersection(k.lower() for k in record)
                    if secrets:
                        raise ValueError(f'{name}: el archivo contiene campos de autenticación')
                    count += 1
            if type(expected[name]) is not int or count != expected[name]:
                raise ValueError(f'{name}: conteo del ZIP distinto del manifest')
            observed[name] = count
        bad_entry = archive.testzip()
        if bad_entry is not None:
            raise ValueError('ZIP con CRC inválido')
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return observed, digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('zip', type=Path, help='Ruta al ZIP exportado en el teléfono')
    args = parser.parse_args()
    try:
        counts, digest = verify(args.zip)
    except (OSError, ValueError, BadZipFile, json.JSONDecodeError) as exc:
        parser.exit(1, f'FALLÓ: {exc}\n')
    print('PASS — ZIP íntegro, JSONL válido y sin tokens de acceso')
    for name, count in counts.items():
        print(f'{name}: {count}')
    print('SHA-256:', digest)


if __name__ == '__main__':
    main()
