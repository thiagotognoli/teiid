#!/bin/bash
# Script de exemplo para usar o filtro de metadados OData

echo "=== Exemplos de uso do Filtro de Metadados OData ==="
echo

# Caminho para o arquivo de metadados original
METADATA_FILE="/home/thiago/Projects/sap/teiid/teiid-jee9/connectors/odata/translator-odata/src/test/resources/fsap-metadata.xml"
PYTHON_EXEC="/home/thiago/Projects/sap/teiid/teiid-jee9/.venv/bin/python"
FILTER_SCRIPT="/home/thiago/Projects/sap/teiid/teiid-jee9/odata_metadata_filter.py"

# Exemplo 1: Filtrar apenas entidades relacionadas a Benefit
echo "1. Filtrando apenas entidades relacionadas a Benefits..."
$PYTHON_EXEC $FILTER_SCRIPT \
  -i "$METADATA_FILE" \
  -o "/tmp/benefits-only-metadata.xml" \
  -e BenefitProgramEnrollmentDetail BenefitInsuranceDependentDetail

echo
echo "Arquivo gerado: /tmp/benefits-only-metadata.xml"
echo

# Exemplo 2: Filtrar apenas algumas entidades específicas
echo "2. Filtrando entidades específicas (Goal, Job, Message)..."
$PYTHON_EXEC $FILTER_SCRIPT \
  -i "$METADATA_FILE" \
  -o "/tmp/specific-entities-metadata.xml" \
  -e GoalComment_1 JobRequisitionPostingFieldControls InnerMessage

echo
echo "Arquivo gerado: /tmp/specific-entities-metadata.xml"
echo

# Exemplo 3: Mostrar ajuda
echo "3. Ajuda do programa:"
$PYTHON_EXEC $FILTER_SCRIPT --help

echo
echo "=== Exemplos concluídos ==="
