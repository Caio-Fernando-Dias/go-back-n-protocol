# Protocolo Go-Back-N via UDP

<img align="center" height="20px" width="60px" alt="" src="https://img.shields.io/badge/Java-version%2017-orange"/> 
<img align="center" height="20px" width="80px" alt="" src="https://img.shields.io/badge/Intellij%20Idea-000?logo=intellij-idea&style=for-the-badge"/>

[//]: # (<p align="center">)

[//]: # (<img src="imgs/Exemplo de DFS.png"/>)

[//]: # (</p>)


<h3><a>1. Contexto e Motivação</a></h3>

<p>A transferência confiável de dados é um dos desafios centrais em redes de computadores. O livro Computer
Networking: A Top-Down Approach (Kurose & Ross) apresenta, no Capítulo 3, uma sequência progressiva de
protocolos de transferência confiável desde o modelo simplificado rdt 1.0 até os protocolos com janela
deslizante Go-Back-N (GBN) e Selective Repeat (SR). Este trabalho foca na implementação prática do
Go-Back-N, levando os conceitos teóricos do livro para um programa Java funcional que transfere arquivos
reais entre dois hosts via sockets UDP.
Como o UDP não oferece garantias de entrega, sequenciamento ou controle de fluxo, toda a lógica de
confiabilidade deverá ser construída pela própria aplicação, exatamente como descrito no modelo de máquina
de estados finita (FSM) do GBN.</p>

<h3><a>2. O Protcolo Go-Back-N (GBN)</a></h3>

<h5><a>2.1 Princípio de funcionamento</a></h5>

<p>
No GBN, o emissor pode ter até N pacotes não reconhecidos (não confirmados) em trânsito simultaneamente 
a chamada janela de transmissão de tamanho N. Os pacotes são numerados com um número de sequência
de k bits, o que limita o espaço de numeração a 2^k valores. O tamanho máximo da janela é, portanto, N ≤ 2^k −
1.
O receptor do GBN adota uma política simples: aceita apenas pacotes em ordem. Se um pacote fora de ordem
for recebido, ele é descartado e o receptor reenvia o ACK do último pacote recebido corretamente. O emissor,
ao expirar um temporizador (timeout), retransmite todos os pacotes dentro da janela a partir do pacote não
confirmado mais antigo daí o nome Go-Back-N.
<p>

<h5><a>2.2 Máquinas de Estados Finitas (FSMs)</a></h5>

<p>A implementação deve seguir fielmente as FSMs apresentadas no livro (Figuras 3.20 e 3.21 da 8ª edição). O
emissor possui dois estados principais:</p>

• Aguardando chamada de cima (Wait for call from above): janela com espaço disponível; aceita novos
segmentos da camada de aplicação.

• Janela cheia / temporizador ativo: transmite pacotes dentro da janela e gerencia timeout e ACKs
recebidos.

<p>O receptor possui um único estado e responde com ACK cumulativo para cada pacote recebido em ordem.</p>

<h5><a>2.3 Variáveis do protocolo</a></h5>


| Variável         | Descrição                                                     |                     
|------------------|---------------------------------------------------------------|
| `base`           | Número de sequência do pacote mais antigo não confirmado      |
| `nextseqnum`     | Próximo número de sequência a ser usado                       |
| `N (windowSize)` | Tamanho da janela de transmissão (configurável pelo usuário)  |
| `expectedseqnum` | Número de sequência esperado pelo receptor (somente em ordem) |

<h3><a>3. Especificação do Trabalho</a></h3>

<h5><a>3.1 Visão geral</a></h5>

<p>O trabalho consiste em implementar dois módulos Java independentes Receptor e Emissor que se
comunicam exclusivamente via sockets UDP, simulando a transferência confiável de um arquivo arbitrário
utilizando o protocolo Go-Back-N.</p>

<h5><a>3.2 Módulo receptor</a></h5>

<p>O Receptor deve ser inicializado antes do Emissor e ficar aguardando conexões em uma porta UDP
configurável (sugestão: porta 5000). Ao receber o primeiro pacote de controle (handshake), o Receptor extrai
os parâmetros da sessão e começa a receber os dados.</p>

O Receptor deve:

• Aguardar na porta UDP configurada por um datagrama inicial de controle contendo os parâmetros da
sessão (probabilidade de perda, nome/path do arquivo de destino, tamanho do arquivo).

• Implementar a FSM do receptor GBN: aceitar apenas pacotes com seqnum == expectedseqnum;
descartar pacotes fora de ordem e reenviar o último ACK enviado.

• Simular perda de pacotes: com base na probabilidade recebida, descartar pacotes de forma aleatória,
sem enviar ACK, forçando retransmissão pelo emissor.

• Salvar o arquivo recebido no path absoluto especificado pelo emissor.

• Ao final da transferência, exibir estatísticas: total de pacotes recebidos, total de pacotes descartados
(simulados como perdidos) e taxa de perda efetiva.

<h5><a>3.3 Módulo emissor</a></h5>

<p>O Emissor é iniciado via linha de comando com os seguintes argumentos obrigatórios:</p>

`java Emissor <arquivo_origem> <IP_destino>:<path_destino> <tamanho_janela> <prob_perda>`

<p>Exemplo de uso:</p>

`java Emissor /home/alice/foto.jpg 192.168.0.10:/tmp/foto_recebida.jpg 8 0.10`

<p>O parâmetro prob_perda é um valor real entre 0,0 e 1,0 (p. ex.: 0,10 = 10% de probabilidade de perda), que
será enviado ao Receptor no pacote de handshake inicial.</p>

<p>O Emissor deve:</p>

• Dividir o arquivo de origem em segmentos de tamanho fixo (sugestão: 1024 bytes de payload), numerados
sequencialmente.

• Implementar a FSM do emissor GBN com janela deslizante de tamanho N.

• Iniciar um temporizador único para o pacote mais antigo não confirmado (base).

• Ao receber um ACK cumulativo de número n, avançar a base da janela para n+1 e reiniciar/cancelar o
temporizador conforme a FSM.

• Em caso de timeout, retransmitir todos os pacotes de base até nextseqnum − 1.

• Enviar um pacote de controle de encerramento (FIN) ao final da transmissão.

• Exibir progresso em tempo real: número de pacotes enviados, ACKs recebidos, retransmissões e taxa de
throughput estimada.

<h5><a>3.4 Formato do datagrama</a></h5>

| Campo           | Tamanho sugerido | Descrição                               |                     
|-----------------|------------------|-----------------------------------------|
| `tipo`          | 1 byte           | 0=DATA, 1=ACK, 2=HANDSHAKE, 3=FIN       |
| `num_seq`       | 4 bytes (int)    | Número de sequência do pacote           |
| `num_ack`       | 4 bytes (int)    | Número de confirmação (somente em ACKs) |
| `tamanho_dados` | 2 bytes (short)  | Quantidade de bytes válidos no payload  |
| `dados`         | até 1024 bytes   | Payload (bytes do arquivo)              |

<h3><a>4. Simulação de perda de pacotes</a></h3>

<p>Como o ambiente de testes é uma rede local (LAN), a probabilidade real de perda de pacotes é praticamente
nula. Para validar o comportamento do GBN, o Receptor deve simular perdas de forma aleatória, descartando
pacotes de dados sem enviar ACK, como se o pacote nunca tivesse chegado.</p>

<p>A lógica de descarte deve ser baseada em geração de número aleatório: para cada pacote recebido (e em
ordem), o Receptor sorteia um valor r ∈ [0,1). Se r < p (onde p é a probabilidade de perda configurada), o
pacote é descartado silenciosamente. Ao final da transferência, a taxa de perda efetiva deve tender à
probabilidade configurada à medida que o número de pacotes transferidos aumenta (Lei dos Grandes
Números).</p>

<p>Atenção: a simulação deve atuar somente sobre pacotes de dados recebidos corretamente em ordem.
Pacotes já fora de ordem são descartados pela própria lógica do GBN e não devem ser contabilizados como
perdas simuladas.</p>

<p>5. Compilação (Corrigir)</p>

| Comando                |  Função                                                                                           |                     
| -----------------------| ------------------------------------------------------------------------------------------------- |
|  `make clean`          | Apaga a última compilação realizada contida na pasta build                                        |
|  `make`                | Executa a compilação do programa utilizando o g++, e o resultado vai para a pasta build           |
|  `make run`            | Executa o programa da pasta build após a realização da compilação                                 |

# Alunos

Estudantes do curso de Redes de Computadores de 2026.

Caio Fernando Dias
Otávio Augusto Miguel
