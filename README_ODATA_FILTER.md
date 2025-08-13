# Filtro de Metadados OData v2

Este programa Python permite filtrar metadados OData v2, mantendo apenas as entidades especificadas e removendo todas as outras e suas referências.

## Funcionalidades

- ✅ Filtra EntitySets mantendo apenas as entidades desejadas
- ✅ Remove EntityTypes não utilizados
- ✅ Remove Associations órfãs (que referenciam entidades removidas)
- ✅ Remove FunctionImports que referenciam entidades removidas
- ✅ Limpa NavigationProperties órfãs
- ✅ Mantém integridade referencial incluindo dependências automaticamente
- ✅ Fornece relatório detalhado das operações realizadas

## Pré-requisitos

- Python 3.7+
- Biblioteca `lxml`

## Instalação

1. Configure o ambiente virtual Python:
```bash
python -m venv .venv
source .venv/bin/activate  # No Linux/Mac
# ou
.venv\Scripts\activate  # No Windows
```

2. Instale as dependências:
```bash
pip install lxml
```

## Uso

### Sintaxe básica:
```bash
python odata_metadata_filter.py -i <arquivo_entrada> -o <arquivo_saída> -e <entidade1> <entidade2> ...
```

### Exemplos práticos:

1. **Filtrar apenas entidades específicas:**
```bash
python odata_metadata_filter.py \
  -i fsap-metadata.xml \
  -o filtered-metadata.xml \
  -e GoalComment_1 InnerMessage BenefitProgramEnrollmentDetail
```

2. **Manter apenas entidades relacionadas a benefícios:**
```bash
python odata_metadata_filter.py \
  -i fsap-metadata.xml \
  -o benefits-only.xml \
  -e BenefitProgramEnrollmentDetail BenefitInsuranceDependentDetail
```

3. **Filtrar entidades de RH:**
```bash
python odata_metadata_filter.py \
  -i metadata.xml \
  -o hr-entities.xml \
  -e Employee Position JobClassification
```

### Opções disponíveis:

- `-i, --input`: Arquivo XML de metadados OData de entrada (obrigatório)
- `-o, --output`: Arquivo XML de metadados OData filtrado de saída (obrigatório)
- `-e, --entities`: Lista de entidades para manter (obrigatório)
- `--include-dependencies`: Incluir entidades relacionadas automaticamente (padrão: True)
- `--dry-run`: Executar sem salvar o arquivo, apenas mostrar o que seria feito
- `-h, --help`: Mostrar ajuda

## Como funciona

O programa segue os seguintes passos:

1. **Carregamento**: Carrega e analisa o arquivo XML de metadados OData
2. **Análise de dependências**: Identifica relacionamentos entre entidades através de NavigationProperties
3. **Expansão**: Inclui automaticamente entidades relacionadas para manter integridade referencial
4. **Filtragem**: Remove elementos não utilizados:
   - EntitySets que referenciam entidades removidas
   - EntityTypes não solicitados
   - Associations órfãs
   - FunctionImports que referenciam entidades removidas
   - NavigationProperties órfãs
5. **Relatório**: Gera um resumo detalhado das operações realizadas
6. **Salvamento**: Salva o arquivo filtrado com formatação adequada

## Exemplo de saída

```
============================================================
RESUMO DO FILTRO DE METADADOS ODATA
============================================================
Arquivo de entrada: fsap-metadata.xml
Arquivo de saída: filtered-metadata.xml

Entidades solicitadas para manter: 2
  - GoalComment_1
  - InnerMessage

Total de entidades mantidas (incluindo dependências): 2
  - GoalComment_1
  - InnerMessage

Total de entidades removidas: 1247
Algumas entidades removidas:
  - BenefitProgramEnrollmentDetail
  - JobRequisitionPostingFieldControls
  - SelfReportSkillMapping
  ... e mais 1244 entidades
============================================================
```

## Estrutura do código

- `ODataMetadataFilter`: Classe principal que gerencia todo o processo
- `load_xml()`: Carrega e analisa o arquivo XML
- `collect_navigation_dependencies()`: Identifica dependências entre entidades
- `filter_*()`: Métodos para filtrar diferentes tipos de elementos
- `generate_summary()`: Gera relatório final

## Limitações conhecidas

- Projetado especificamente para OData v2
- Funciona melhor com metadados bem-formados seguindo o padrão EDM
- Arquivos muito grandes podem levar tempo para processar

## Troubleshooting

### Erro de namespace
Se você receber erros relacionados a namespace, verifique se o arquivo XML está bem-formado e segue o padrão OData v2.

### Performance em arquivos grandes
Para arquivos muito grandes (>50MB), o processamento pode levar alguns minutos. Considere usar um subconjunto menor de entidades para teste.

### Dependências quebradas
Se o arquivo resultante tiver problemas de integridade, verifique se todas as entidades relacionadas foram incluídas corretamente.

## Contribuição

Este programa foi desenvolvido para facilitar o trabalho com metadados OData v2 grandes, permitindo criar subconjuntos focados para desenvolvimento e teste.

## Licença

MIT License - veja o arquivo LICENSE para detalhes.
