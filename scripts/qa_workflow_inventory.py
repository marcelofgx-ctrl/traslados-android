#!/usr/bin/env python3
"""Inventory only: never mutate GitHub workflows, source, or deployment."""
import argparse
import json
import os
from pathlib import Path
from urllib.parse import quote

def group(name):
    if name.startswith(("build-", "quicktrip-v7-apks", "publicar-cliente-apk")):
        return "Compilación / legado (revisar, NO eliminar)"
    if name.startswith(("qa-", "check-", "smoke-", "pwa-")):
        return "QA / smoke / revisión"
    if name.startswith(("fast-", "pr-", "validate-")):
        return "Contratos / protección CI"
    if name.startswith(("deploy-", "sync-")):
        return "Publicación / sincronización (NO ejecutar)"
    return "Mantenimiento / otros"

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",default="quicktrip-v90-pilot-evidence/inventory")
    args=parser.parse_args()
    root=Path(".github/workflows")
    if not root.is_dir():
        raise SystemExit("Cannot find .github/workflows: run from repo root")
    files=sorted((p for p in root.iterdir() if p.is_file()),key=lambda x:x.name)
    result=[]
    for p in files:
        yml=p.suffix.lower() in (".yml",".yaml")
        result.append({
            "file":p.name, "type":"workflow_yaml" if yml else "non_yaml",
            "bytes":p.stat().st_size,
            "category":group(p.name) if yml else "No YAML; revisar archivo fuera de lugar",
            "github_url":"https://github.com/marcelofgx-ctrl/traslados-android/blob/"
                +branch+"/"+quote(".github/workflows/"+p.name),
        })
    yaml_count=sum(x["type"]=="workflow_yaml" for x in result)
    non_count=len(result)-yaml_count
    categories={}
    for item in result:
        categories[item["category"]]=categories.get(item["category"],0)+1
    data={
        "date":"2026-10-26", "reference_base":"8d882e63e1a4e779f956868af64c9e6158c4c0ac",
        "branch":branch, "yaml_count":yaml_count, "other_count":non_count,
        "total_files":len(result), "category_counts":categories,
        "warning":"Filesystem inventory; not GitHub Actions enabled/disabled state, execution status, usage or deletion advice.",
        "files":result
    }
    out=Path(args.output)
    out.mkdir(parents=True,exist_ok=True)
    (out/"WORKFLOWS_INVENTORY.json").write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf8")
    md=[
        "# Inventario workflows — piloto v90", "",
        f"Rama: \`{branch}\` · base: \`{data['reference_base']}\`", "",
        f"- YAML: **{yaml_count}** · otros: **{non_count}** · total: **{len(result)}**",
        "- Este inventario cuenta ARCHIVOS, no workflows habilitados ni ejecuciones.",
        "- No se borró, deshabilitó ni ejecutó un workflow productivo.","",
        "## Clasificación por nombre (requiere auditoría de triggers)", ""
    ]
    md += [f"- {k}: {n}" for k,n in sorted(categories.items())]
    md += ["", "## Todos los archivos", "", "| Tipo / clasificación | Archivo | Tamaño (bytes) |",
           "|---|---|---:|"]
    md += [f"| {x['category']} | \`{x['file'].replace('|','')}\` | {x['bytes']} |" for x in result]
    (out/"WORKFLOWS_INVENTORY.md").write_text("\n".join(md)+"\n",encoding="utf8")
    print(f"INVENTORY PASS: {len(result)} entries; {yaml_count} YAML; {non_count} non-YAML. Zero writes to repository.")
    print(json.dumps(categories,ensure_ascii=False,sort_keys=True))
    if not any(x["file"]=="qa-quicktrip-v90-acceleration-pilot.yml" for x in result):
        raise SystemExit("ERROR: pilot workflow missing")
    if not any(x["file"]=="qa-quicktrip-v90-route-sandbox.yml" for x in result):
        raise SystemExit("ERROR: v90 baseline workflow missing")

branch="qa/quicktrip-v90-acceleration-pilot-20261026"
if __name__=="__main__":
    main()
