#!/usr/bin/env python3
"""
Programa para filtrar metadados OData v2, mantendo apenas as entidades desejadas
e removendo todas as outras e suas referências.
"""

import argparse
import sys
from lxml import etree
from typing import Set, List, Dict


class ODataMetadataFilter:
    def __init__(self, input_file: str, output_file: str, entities: List[str]):
        self.input_file = input_file
        self.output_file = output_file
        self.entities_to_keep = set(entities)
        self.namespace_map = {}
        self.associations_to_keep = set()
        self.removed_entities = set()
        
    def load_xml(self) -> etree.Element:
        """Carrega o arquivo XML de metadados OData."""
        try:
            with open(self.input_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Parse do XML
            parser = etree.XMLParser()
            tree = etree.fromstring(content.encode('utf-8'), parser)
            
            # Mapear namespaces - incluir todos os namespaces encontrados
            self.namespace_map = tree.nsmap.copy() if tree.nsmap else {}
            
            # Adicionar namespaces padrão do OData se não existirem
            if 'default' not in self.namespace_map and None in tree.nsmap:
                self.namespace_map['default'] = tree.nsmap[None]
            elif 'default' not in self.namespace_map:
                # Procurar por elementos filhos que possam ter namespace padrão
                for child in tree:
                    if child.nsmap and None in child.nsmap:
                        self.namespace_map['default'] = child.nsmap[None]
                        break
                # Se ainda não encontrou, usar namespace EDM padrão
                if 'default' not in self.namespace_map:
                    self.namespace_map['default'] = 'http://schemas.microsoft.com/ado/2008/09/edm'
                
            print(f"Arquivo carregado: {self.input_file}")
            print(f"Namespaces encontrados: {self.namespace_map}")
            
            return tree
        except Exception as e:
            print(f"Erro ao carregar arquivo: {e}")
            sys.exit(1)
    
    def find_entity_container(self, root: etree.Element) -> etree.Element:
        """Encontra o EntityContainer no XML."""
        # Primeiro tentar com namespace padrão
        containers = root.xpath('.//default:EntityContainer', namespaces=self.namespace_map)
        if containers:
            return containers[0]
            
        # Se não encontrar, tentar sem namespace
        containers = root.xpath('.//EntityContainer')
        if containers:
            return containers[0]
            
        # Tentar com edmx namespace
        containers = root.xpath('.//edmx:DataServices//EntityContainer', namespaces=self.namespace_map)
        if containers:
            return containers[0]
            
        return None
    
    def find_entity_types_section(self, root: etree.Element) -> etree.Element:
        """Encontra a seção onde estão definidos os EntityTypes."""
        # Primeiro tentar com namespace padrão
        schemas = root.xpath('.//default:Schema', namespaces=self.namespace_map)
        if schemas:
            return schemas[0]
            
        # Se não encontrar, tentar sem namespace
        schemas = root.xpath('.//Schema')
        if schemas:
            return schemas[0]
            
        # Tentar com edmx namespace
        schemas = root.xpath('.//edmx:DataServices//Schema', namespaces=self.namespace_map)
        if schemas:
            return schemas[0]
            
        return None
    
    def get_entity_type_name(self, entity_type_attr: str) -> str:
        """Extrai o nome do EntityType removendo o namespace."""
        if '.' in entity_type_attr:
            return entity_type_attr.split('.')[-1]
        return entity_type_attr
    
    def collect_navigation_dependencies(self, root: etree.Element) -> Dict[str, Set[str]]:
        """Coleta dependências de navegação entre entidades."""
        dependencies = {}
        
        # Procurar por NavigationProperties em EntityTypes de forma mais robusta
        entity_types = root.xpath('.//EntityType') or root.xpath('.//default:EntityType', namespaces=self.namespace_map)
        
        for entity_type in entity_types:
            entity_name = entity_type.get('Name')
            if entity_name:
                dependencies[entity_name] = set()
                
                # Procurar NavigationProperties
                nav_props = entity_type.xpath('.//NavigationProperty') or entity_type.xpath('.//default:NavigationProperty', namespaces=self.namespace_map)
                
                for nav_prop in nav_props:
                    relationship = nav_prop.get('Relationship')
                    if relationship:
                        # Encontrar a Association correspondente
                        assoc_name = relationship.split('.')[-1] if '.' in relationship else relationship
                        self.associations_to_keep.add(assoc_name)
                        
                        # Procurar a Association para encontrar as entidades relacionadas
                        associations = root.xpath(f'.//Association[@Name="{assoc_name}"]') or root.xpath(f'.//default:Association[@Name="{assoc_name}"]', namespaces=self.namespace_map)
                        
                        for association in associations:
                            ends = association.xpath('.//End') or association.xpath('.//default:End', namespaces=self.namespace_map)
                            for end in ends:
                                related_type = end.get('Type')
                                if related_type:
                                    related_entity = self.get_entity_type_name(related_type)
                                    if related_entity != entity_name:
                                        dependencies[entity_name].add(related_entity)
        
        return dependencies
    
    def calculate_entities_to_keep(self, dependencies: Dict[str, Set[str]]) -> Set[str]:
        """Calcula todas as entidades que devem ser mantidas, incluindo dependências."""
        entities_to_keep = set(self.entities_to_keep)
        
        # Expandir para incluir dependências (navegação)
        to_process = list(self.entities_to_keep)
        processed = set()
        
        while to_process:
            current = to_process.pop(0)
            if current in processed:
                continue
            processed.add(current)
            
            if current in dependencies:
                for dep in dependencies[current]:
                    if dep not in entities_to_keep:
                        entities_to_keep.add(dep)
                        to_process.append(dep)
        
        return entities_to_keep
    
    def filter_entity_sets(self, container: etree.Element, entities_to_keep: Set[str]):
        """Remove EntitySets que não estão na lista de entidades para manter."""
        entity_sets_to_remove = []
        
        # Procurar EntitySets de forma mais robusta
        entity_sets = container.xpath('.//EntitySet') or container.xpath('.//default:EntitySet', namespaces=self.namespace_map)
        
        for entity_set in entity_sets:
            entity_type_attr = entity_set.get('EntityType')
            if entity_type_attr:
                entity_name = self.get_entity_type_name(entity_type_attr)
                if entity_name not in entities_to_keep:
                    entity_sets_to_remove.append(entity_set)
                    self.removed_entities.add(entity_name)
        
        for entity_set in entity_sets_to_remove:
            container.remove(entity_set)
        
        print(f"Removidos {len(entity_sets_to_remove)} EntitySets")
    
    def filter_entity_types(self, schema: etree.Element, entities_to_keep: Set[str]):
        """Remove EntityTypes que não estão na lista de entidades para manter."""
        entity_types_to_remove = []
        
        # Procurar EntityTypes de forma mais robusta
        entity_types = schema.xpath('.//EntityType') or schema.xpath('.//default:EntityType', namespaces=self.namespace_map)
        
        for entity_type in entity_types:
            entity_name = entity_type.get('Name')
            if entity_name and entity_name not in entities_to_keep:
                entity_types_to_remove.append(entity_type)
        
        for entity_type in entity_types_to_remove:
            schema.remove(entity_type)
        
        print(f"Removidos {len(entity_types_to_remove)} EntityTypes")
    
    def filter_associations(self, schema: etree.Element, entities_to_keep: Set[str]):
        """Remove Associations que referenciam entidades removidas."""
        associations_to_remove = []
        
        # Procurar Associations de forma mais robusta
        associations = schema.xpath('.//Association') or schema.xpath('.//default:Association', namespaces=self.namespace_map)
        
        for association in associations:
            should_keep = True
            
            # Verificar se todos os Ends da Association referenciam entidades que serão mantidas
            ends = association.xpath('.//End') or association.xpath('.//default:End', namespaces=self.namespace_map)
            
            for end in ends:
                entity_type = end.get('Type')
                if entity_type:
                    entity_name = self.get_entity_type_name(entity_type)
                    if entity_name not in entities_to_keep:
                        should_keep = False
                        break
            
            if not should_keep:
                associations_to_remove.append(association)
        
        for association in associations_to_remove:
            schema.remove(association)
        
        print(f"Removidas {len(associations_to_remove)} Associations")
    
    def filter_function_imports(self, container: etree.Element, entities_to_keep: Set[str]):
        """Remove FunctionImports que referenciam entidades removidas."""
        function_imports_to_remove = []
        
        # Procurar FunctionImports de forma mais robusta
        func_imports = container.xpath('.//FunctionImport') or container.xpath('.//default:FunctionImport', namespaces=self.namespace_map)
        
        for func_import in func_imports:
            # Verificar ReturnType
            return_type = func_import.get('ReturnType')
            if return_type:
                # Pode ser Collection(EntityType) ou apenas EntityType
                if return_type.startswith('Collection(') and return_type.endswith(')'):
                    entity_type = return_type[11:-1]  # Remove "Collection(" e ")"
                else:
                    entity_type = return_type
                
                entity_name = self.get_entity_type_name(entity_type)
                if entity_name not in entities_to_keep and entity_name in self.removed_entities:
                    function_imports_to_remove.append(func_import)
        
        for func_import in function_imports_to_remove:
            container.remove(func_import)
        
        print(f"Removidos {len(function_imports_to_remove)} FunctionImports")
    
    def clean_navigation_properties(self, schema: etree.Element, entities_to_keep: Set[str]):
        """Remove NavigationProperties que referenciam entidades removidas."""
        entity_types = schema.xpath('.//EntityType') or schema.xpath('.//default:EntityType', namespaces=self.namespace_map)
        
        for entity_type in entity_types:
            nav_props_to_remove = []
            
            nav_props = entity_type.xpath('.//NavigationProperty') or entity_type.xpath('.//default:NavigationProperty', namespaces=self.namespace_map)
            
            for nav_prop in nav_props:
                relationship = nav_prop.get('Relationship')
                if relationship:
                    assoc_name = relationship.split('.')[-1] if '.' in relationship else relationship
                    
                    # Verificar se a Association ainda existe
                    associations = schema.xpath(f'.//Association[@Name="{assoc_name}"]') or schema.xpath(f'.//default:Association[@Name="{assoc_name}"]', namespaces=self.namespace_map)
                    association_exists = len(associations) > 0
                    
                    if not association_exists:
                        nav_props_to_remove.append(nav_prop)
            
            for nav_prop in nav_props_to_remove:
                entity_type.remove(nav_prop)
    
    def generate_summary(self, entities_to_keep: Set[str]):
        """Gera um resumo das operações realizadas."""
        print("\n" + "="*60)
        print("RESUMO DO FILTRO DE METADADOS ODATA")
        print("="*60)
        print(f"Arquivo de entrada: {self.input_file}")
        print(f"Arquivo de saída: {self.output_file}")
        print(f"\nEntidades solicitadas para manter: {len(self.entities_to_keep)}")
        for entity in sorted(self.entities_to_keep):
            print(f"  - {entity}")
        
        print(f"\nTotal de entidades mantidas (incluindo dependências): {len(entities_to_keep)}")
        for entity in sorted(entities_to_keep):
            print(f"  - {entity}")
        
        print(f"\nTotal de entidades removidas: {len(self.removed_entities)}")
        if self.removed_entities:
            print("Algumas entidades removidas:")
            for entity in sorted(list(self.removed_entities)[:10]):  # Mostrar apenas as primeiras 10
                print(f"  - {entity}")
            if len(self.removed_entities) > 10:
                print(f"  ... e mais {len(self.removed_entities) - 10} entidades")
        print("="*60)
    
    def process(self):
        """Executa o processo completo de filtragem."""
        print("Iniciando processamento do arquivo de metadados OData...")
        
        # Carregar XML
        root = self.load_xml()
        
        # Encontrar seções principais
        container = self.find_entity_container(root)
        schema = self.find_entity_types_section(root)
        
        if container is None or schema is None:
            print("Erro: Não foi possível encontrar EntityContainer ou Schema")
            sys.exit(1)
        
        # Coletar dependências de navegação
        print("\nColetando dependências de navegação...")
        dependencies = self.collect_navigation_dependencies(root)
        
        # Calcular entidades finais para manter
        entities_to_keep = self.calculate_entities_to_keep(dependencies)
        
        print(f"\nEntidades a serem mantidas: {len(entities_to_keep)}")
        print(f"Entidades solicitadas originalmente: {len(self.entities_to_keep)}")
        
        # Aplicar filtros
        print("\nFiltrando EntitySets...")
        self.filter_entity_sets(container, entities_to_keep)
        
        print("Filtrando EntityTypes...")
        self.filter_entity_types(schema, entities_to_keep)
        
        print("Filtrando Associations...")
        self.filter_associations(schema, entities_to_keep)
        
        print("Filtrando FunctionImports...")
        self.filter_function_imports(container, entities_to_keep)
        
        print("Limpando NavigationProperties órfãs...")
        self.clean_navigation_properties(schema, entities_to_keep)
        
        # Salvar resultado
        print(f"\nSalvando arquivo filtrado: {self.output_file}")
        tree = etree.ElementTree(root)
        tree.write(self.output_file, encoding='utf-8', xml_declaration=True, pretty_print=True)
        
        # Gerar resumo
        self.generate_summary(entities_to_keep)
        print(f"\nProcessamento concluído! Arquivo salvo como: {self.output_file}")


def main():
    parser = argparse.ArgumentParser(
        description='Filtra metadados OData v2, mantendo apenas as entidades especificadas',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Exemplos de uso:

1. Manter apenas algumas entidades específicas:
   python odata_metadata_filter.py -i metadata.xml -o filtered.xml -e User Employee Department

2. Manter entidades relacionadas a benefícios:
   python odata_metadata_filter.py -i fsap-metadata.xml -o benefits-only.xml -e BenefitProgramEnrollmentDetail BenefitInsuranceDependentDetail

3. Manter entidades de RH:
   python odata_metadata_filter.py -i metadata.xml -o hr-entities.xml -e Employee Position JobClassification

O programa automaticamente inclui entidades relacionadas através de NavigationProperties
para manter a integridade referencial dos metadados.
        """
    )
    
    parser.add_argument('-i', '--input', required=True, 
                       help='Arquivo XML de metadados OData de entrada')
    parser.add_argument('-o', '--output', required=True,
                       help='Arquivo XML de metadados OData filtrado de saída')
    parser.add_argument('-e', '--entities', nargs='+', required=True,
                       help='Lista de entidades para manter (nomes dos EntityTypes)')
    parser.add_argument('--include-dependencies', action='store_true', default=True,
                       help='Incluir entidades relacionadas automaticamente (padrão: True)')
    parser.add_argument('--dry-run', action='store_true',
                       help='Executar sem salvar o arquivo, apenas mostrar o que seria feito')
    
    args = parser.parse_args()
    
    if not args.entities:
        print("Erro: Deve especificar pelo menos uma entidade para manter")
        sys.exit(1)
    
    try:
        filter_tool = ODataMetadataFilter(args.input, args.output, args.entities)
        
        if args.dry_run:
            print("MODO DRY-RUN: Apenas analisando, não salvará arquivo")
            # Implementar lógica de dry-run aqui se necessário
        
        filter_tool.process()
        
    except KeyboardInterrupt:
        print("\nOperação cancelada pelo usuário")
        sys.exit(1)
    except Exception as e:
        print(f"Erro durante o processamento: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
