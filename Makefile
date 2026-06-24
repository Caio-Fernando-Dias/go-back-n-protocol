# Define as pastas do projeto
BUILD_DIR = build
SRC_DIR = src
DATA_DIR = data

# Regra padrão ao executar apenas 'make'
all: compile

# Cria as pastas build e data, e compila os ficheiros .java
compile:
	mkdir -p $(BUILD_DIR)
	mkdir -p $(DATA_DIR)
	javac -d $(BUILD_DIR) $(SRC_DIR)/*.java

# Remove as pastas temporárias por completo
clean:
	rm -rf $(BUILD_DIR)
	rm -rf $(DATA_DIR)

# Executa os programas
gerador:
	mkdir -p $(DATA_DIR)
	cd $(SRC_DIR) && java -cp ../$(BUILD_DIR) GeradorArquivo

receptor:
	cd $(SRC_DIR) && java -cp ../$(BUILD_DIR) Receptor

# Exemplo de uso atualizado para apontar para a pasta data:
# make emissor ARGS="../data/arquivo_teste.txt localhost:../data/recebido_teste.txt 8 0.15"
emissor:
	cd $(SRC_DIR) && java -cp ../$(BUILD_DIR) Emissor $(ARGS)