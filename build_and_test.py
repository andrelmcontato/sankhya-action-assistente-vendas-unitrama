# -*- coding: utf-8 -*-
"""
Script de Compilação e Execução de Testes Automatizados (TDD / JUnit 4.12)
Target: Java 1.8 | Encoding: UTF-8
"""

import os
import sys
import glob
import subprocess

def main():
    print("=" * 75)
    print("  SUITE DE TESTES: ASSISTENTE DE VENDAS UNITRAMA (TDD / JUNIT 4.12)")
    print("=" * 75)

    project_root = os.path.dirname(os.path.abspath(__file__))
    src_dir = os.path.join(project_root, "src")
    test_dir = os.path.join(project_root, "test", "java")
    out_prod = os.path.join(project_root, "out", "production", "AssistenteVendasUnitrama")
    out_test = os.path.join(project_root, "out", "test", "AssistenteVendasUnitrama")

    sk_lib_dir = r"C:\sk-java\Binários para Projetos"
    test_lib_dir = r"C:\Users\andre\Arquivos\Projetos Antigravity\Projeto Sankhya Binarios\Binarios\Bibliotecas"
    java_exe = r"C:\sk-java\jdk1.8.0_121\bin\java.exe"
    javac_exe = r"C:\sk-java\jdk1.8.0_121\bin\javac.exe"

    os.makedirs(out_prod, exist_ok=True)
    os.makedirs(out_test, exist_ok=True)

    # 1. Localizar JARs
    sk_jars = glob.glob(os.path.join(sk_lib_dir, "*.jar"))
    test_jars = [
        os.path.join(test_lib_dir, "junit-4.12.jar"),
        os.path.join(test_lib_dir, "hamcrest-all-1.3.jar"),
        os.path.join(test_lib_dir, "mockito-core-1.10.19.jar"),
        os.path.join(test_lib_dir, "objenesis-2.1.jar")
    ]

    for tj in test_jars:
        if not os.path.exists(tj):
            print(f"[ERRO] Biblioteca de teste nao encontrada: {tj}")
            sys.exit(1)

    # 2. Compilar Fontes de Produção
    print("[1/4] Compilando fontes de produção...")
    prod_files = []
    for root, dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                prod_files.append(os.path.join(root, f))

    prod_cp = ";".join([out_prod] + sk_jars)
    cmd_compile_prod = [
        javac_exe,
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-target", "1.8",
        "-cp", prod_cp,
        "-d", out_prod
    ] + prod_files

    res_prod = subprocess.run(cmd_compile_prod, capture_output=True, text=True)
    if res_prod.returncode != 0:
        print("[FALHA NA COMPILAÇÃO DE PRODUÇÃO]")
        print(res_prod.stdout)
        print(res_prod.stderr)
        sys.exit(1)
    print(f"  -> {len(prod_files)} classes de produção compiladas com sucesso.")

    # 3. Compilar Classes de Teste
    print("[2/4] Compilando classes de teste unitário...")
    test_files = []
    test_classes = []
    for root, dirs, files in os.walk(test_dir):
        for f in files:
            if f.endswith("Test.java"):
                full_path = os.path.join(root, f)
                test_files.append(full_path)
                rel_path = os.path.relpath(full_path, test_dir)
                cls_name = rel_path.replace(os.sep, ".").replace(".java", "")
                test_classes.append(cls_name)

    test_cp = ";".join([out_prod, out_test] + test_jars + sk_jars)
    cmd_compile_test = [
        javac_exe,
        "-encoding", "UTF-8",
        "-source", "1.8",
        "-target", "1.8",
        "-cp", test_cp,
        "-d", out_test
    ] + test_files

    res_test = subprocess.run(cmd_compile_test, capture_output=True, text=True)
    if res_test.returncode != 0:
        print("[FALHA NA COMPILAÇÃO DOS TESTES]")
        print(res_test.stdout)
        print(res_test.stderr)
        sys.exit(1)
    print(f"  -> {len(test_files)} classes de teste compiladas com sucesso.")

    # 4. Executar JUnitCore
    print(f"[3/4] Executando {len(test_classes)} suítes de teste via JUnitCore...")
    run_cp = ";".join([out_test, out_prod] + test_jars + sk_jars)
    cmd_run = [
        java_exe,
        "-cp", run_cp,
        "org.junit.runner.JUnitCore"
    ] + test_classes

    res_run = subprocess.run(cmd_run, capture_output=True, text=True)
    print("=" * 75)
    print("  RESULTADO DA EXECUÇÃO DOS TESTES:")
    print("=" * 75)
    print(res_run.stdout)
    if res_run.stderr:
        print("STDERR:", res_run.stderr)

    if res_run.returncode != 0:
        print("[FALHA NOS TESTES UNITÁRIOS]")
        sys.exit(1)

    print("[4/4] Todos os testes passaram com 100% de sucesso!")
    print("=" * 75)

if __name__ == "__main__":
    main()
