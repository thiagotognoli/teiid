#!/bin/bash

# Script de conveniência para filtrar metadados OData
# Uso: ./filter_odata.sh <entidades...>

set -e

# Configurações
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_EXEC="$SCRIPT_DIR/.venv/bin/python"
FILTER_SCRIPT="$SCRIPT_DIR/odata_metadata_filter.py"
INPUT_FILE="$SCRIPT_DIR/connectors/odata/translator-odata/src/test/resources/fsap-metadata.xml"

# Verificar se o Python virtual env existe
if [[ ! -f "$PYTHON_EXEC" ]]; then
    echo "❌ Ambiente virtual Python não encontrado em: $PYTHON_EXEC"
    echo "Execute: python -m venv .venv && .venv/bin/pip install lxml"
    exit 1
fi

# Verificar se o arquivo de entrada existe
if [[ ! -f "$INPUT_FILE" ]]; then
    echo "❌ Arquivo de metadados não encontrado em: $INPUT_FILE"
    exit 1
fi

# Verificar argumentos
if [[ $# -eq 0 ]]; then
    echo "Uso: $0 <entidade1> [entidade2] [entidade3] ..."
    echo ""
    echo "Exemplos:"
    echo "  $0 GoalComment_1 InnerMessage"
    echo "  $0 BenefitProgramEnrollmentDetail BenefitInsuranceDependentDetail"
    echo "  $0 Employee Position JobClassification"
    echo ""
    echo "Para ver todas as opções disponíveis:"
    echo "  $PYTHON_EXEC $FILTER_SCRIPT --help"
    exit 1
fi

# Gerar nome do arquivo de saída baseado nas entidades
ENTITIES="$*"
SAFE_ENTITIES=$(echo "$ENTITIES" | tr ' ' '_' | tr '[:upper:]' '[:lower:]')
OUTPUT_FILE="$SCRIPT_DIR/filtered_metadata_${SAFE_ENTITIES}.xml"

echo "🚀 Iniciando filtragem de metadados OData..."
echo "📁 Arquivo de entrada: $INPUT_FILE"
echo "📁 Arquivo de saída: $OUTPUT_FILE"
echo "🎯 Entidades solicitadas: $ENTITIES"
echo ""

# Executar o filtro
"$PYTHON_EXEC" "$FILTER_SCRIPT" \
    -i "$INPUT_FILE" \
    -o "$OUTPUT_FILE" \
    -e $ENTITIES

echo ""
echo "✅ Filtragem concluída!"
echo "📁 Arquivo gerado: $OUTPUT_FILE"

# Mostrar tamanho dos arquivos
if command -v du >/dev/null 2>&1; then
    echo ""
    echo "📊 Comparação de tamanhos:"
    echo "   Original: $(du -h "$INPUT_FILE" | cut -f1)"
    echo "   Filtrado: $(du -h "$OUTPUT_FILE" | cut -f1)"
fi

# Sugerir próximos passos
echo ""
echo "💡 Próximos passos:"
echo "   - Verificar o arquivo: cat \"$OUTPUT_FILE\""
echo "   - Usar em testes: cp \"$OUTPUT_FILE\" src/test/resources/"
echo "   - Validar XML: xmllint --format \"$OUTPUT_FILE\" > /dev/null && echo 'XML válido'"
