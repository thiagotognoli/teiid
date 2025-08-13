#!/bin/bash

# Script de demonstração do Filtro de Metadados OData v2

echo "🎯 Demonstração do Filtro de Metadados OData v2"
echo "================================================="
echo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_EXEC="$SCRIPT_DIR/.venv/bin/python"
FILTER_SCRIPT="$SCRIPT_DIR/odata_metadata_filter.py"

echo "📋 Informações do ambiente:"
echo "   • Python: $PYTHON_EXEC"
echo "   • Script: $FILTER_SCRIPT"
echo "   • Diretório: $SCRIPT_DIR"
echo

# Verificar se tudo está configurado
if [[ ! -f "$PYTHON_EXEC" ]]; then
    echo "❌ Ambiente Python não encontrado. Execute:"
    echo "   python -m venv .venv"
    echo "   .venv/bin/pip install lxml"
    exit 1
fi

echo "✅ Ambiente configurado corretamente!"
echo

# Demonstração 1: Arquivo pequeno com 2 entidades
echo "📝 Demonstração 1: Filtrar 2 entidades do arquivo de teste"
echo "-----------------------------------------------------------"
"$PYTHON_EXEC" "$FILTER_SCRIPT" \
    -i test-metadata.xml \
    -o demo1-filtered.xml \
    -e GoalComment_1 InnerMessage

echo
echo "✅ Demonstração 1 concluída! Arquivo: demo1-filtered.xml"
echo

# Demonstração 2: Arquivo pequeno com 1 entidade
echo "📝 Demonstração 2: Filtrar apenas 1 entidade"
echo "---------------------------------------------"
"$PYTHON_EXEC" "$FILTER_SCRIPT" \
    -i test-metadata.xml \
    -o demo2-filtered.xml \
    -e BenefitProgramEnrollmentDetail

echo
echo "✅ Demonstração 2 concluída! Arquivo: demo2-filtered.xml"
echo

# Mostrar ajuda
echo "📝 Demonstração 3: Opções disponíveis do programa"
echo "--------------------------------------------------"
"$PYTHON_EXEC" "$FILTER_SCRIPT" --help

echo
echo "🎉 Demonstrações concluídas!"
echo
echo "📁 Arquivos gerados:"
echo "   • demo1-filtered.xml (2 entidades)"
echo "   • demo2-filtered.xml (1 entidade)"
echo
echo "💡 Para usar com o arquivo real do SAP:"
echo "   $PYTHON_EXEC $FILTER_SCRIPT \\"
echo "     -i connectors/odata/translator-odata/src/test/resources/fsap-metadata.xml \\"
echo "     -o meu-filtro.xml \\"
echo "     -e MinhaEntidade1 MinhaEntidade2"
echo
echo "🛠️  Ou use o script de conveniência:"
echo "   ./filter_odata.sh MinhaEntidade1 MinhaEntidade2"
