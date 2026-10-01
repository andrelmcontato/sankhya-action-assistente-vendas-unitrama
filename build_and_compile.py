# -*- coding: utf-8 -*-
"""
Script de Compilacao e Empacotamento do Projeto AssistenteVendas
Target: Java 1.8 | Encoding: UTF-8
Bibliotecas: C:\\sk-java\\Binarios para Projetos\\*.jar
Saida: out/artifacts/AssistenteVendas_jar/AssistenteVendas.jar
"""

import os
import sys
import shutil
import subprocess
import glob

def main():
    print("=" * 70)
    print("  BUILD & COMPILE: ASSISTENTE DE VENDAS UNITRAMA (SANKHYA NATIVE)")
    print("=" * 70)

    project_root = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(project_root, "src")
    out_classes = os.path.join(project_root, "out", "production", "AssistenteVendasUnitrama")
    out_artifact = os.path.join(project_root, "out", "artifacts", "AssistenteVendasUnitrama_jar")
    jar_path = os.path.join(out_artifact, "AssistenteVendasUnitrama.jar")
    lib_dir = r"C:\sk-java\Binários para Projetos"

    # 1. Verificar diretorios essenciais
    if not os.path.exists(lib_dir):
        print(f"[ERRO] Diretorio de bibliotecas nao encontrado: {lib_dir}")
        sys.exit(1)

    # 2. Localizar arquivos Java
    java_files = []
    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith(".java"):
                java_files.append(os.path.join(root, file))

    if not java_files:
        print("[ERRO] Nenhum arquivo .java encontrado em src/")
        sys.exit(1)

    print(f"[1/4] Localizados {len(java_files)} fontes Java para compilação.")

    # 3. Montar Classpath
    jars = glob.glob(os.path.join(lib_dir, "*.jar"))
    classpath = ";".join(jars)
    print(f"[2/4] Classpath configurado com {len(jars)} bibliotecas do ERP Sankhya.")

    # 4. Criar pastas de saida
    os.makedirs(out_classes, exist_ok=True)
    os.makedirs(out_artifact, exist_ok=True)

    # 5. Executar javac
    print("[3/4] Compilando fontes Java (Target: 1.8, UTF-8)...")
    cmd_javac = [
        "javac",
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-target", "1.8",
        "-cp", classpath,
        "-d", out_classes
    ] + java_files

    result = subprocess.run(cmd_javac, capture_output=True, text=True)
    if result.returncode != 0:
        print("[FALHA DE COMPILAÇÃO]")
        print(result.stdout)
        print(result.stderr)
        sys.exit(1)
    else:
        print("  -> Compilação concluída com sucesso (0 erros).")

    # 6. Copiar Resources para out_classes
    resources_dir = os.path.join(src_dir, "resources")
    if os.path.exists(resources_dir):
        print("  -> Copiando arquivos de recursos (web, sql, popUp)...")
        for item in os.listdir(resources_dir):
            s = os.path.join(resources_dir, item)
            d = os.path.join(out_classes, item)
            if os.path.isdir(s):
                if os.path.exists(d):
                    shutil.rmtree(d)
                shutil.copytree(s, d)
            else:
                shutil.copy2(s, d)

    popup_src = os.path.join(src_dir, "popUp")
    if os.path.exists(popup_src):
        popup_dst = os.path.join(out_classes, "popUp")
        if os.path.exists(popup_dst):
            shutil.rmtree(popup_dst)
        shutil.copytree(popup_src, popup_dst)
        print("  -> Pasta popUp copiada para out_classes.")

    # 7. Gerar JAR
    print("[4/4] Empacotando artefato final AssistenteVendas.jar...")
    cmd_jar = [
        "jar",
        "cf",
        jar_path,
        "-C", out_classes,
        "."
    ]
    result_jar = subprocess.run(cmd_jar, capture_output=True, text=True)
    if result_jar.returncode != 0:
        print("[ERRO AO GERAR JAR]")
        print(result_jar.stderr)
        sys.exit(1)

    jar_size = os.path.getsize(jar_path)
    print("=" * 70)
    print("  SUCESSO ABSOLUTO!")
    print(f"  Artefato gerado: {jar_path}")
    print(f"  Tamanho: {jar_size / 1024:.2f} KB")
    print("=" * 70)

if __name__ == "__main__":
    main()
