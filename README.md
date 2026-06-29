# LumenGPS 🗺️✨

*Read this in other languages: [English](#english), [Português](#português)*

---

<h2 id="english">🇬🇧 English</h2>

LumenGPS is a **server-side** mod for Minecraft Fabric that adds a GPS routing system using glowing particles. The mod uses an asynchronous A* (A-Star) pathfinding algorithm to guide players to saved waypoints.

Since it is a **server-side** mod, **players do not need to install the mod on their client** to see the particle trail and use the commands! It runs fully on the server tick.

### 🌟 Features

- **Fully Server-Side:** No client mod required! Players can see the glowing trail and use all commands using their vanilla Minecraft client.
- **Offline Chunk Pathfinding:** Calculates paths even through unloaded or unexplored chunks by reading `.mca` region files directly from the server disk asynchronously.
- **Visual Navigation (Glowing Trail):** Particles guide you along the correct path, avoiding obstacles, descending cliffs, and climbing blocks.
- **Server Waypoints (Global):** Operators (OPs) can register global waypoints (like `/spawn`, `/village`) that any player can view and navigate to.
- **Interactive Prompts (Emoteless & Safe):** Clickable chat actions with overwrite confirmations, removal prompts, and smart conflict resolution when personal and server waypoints share the same name.
- **Optimized A\* Pathfinding:** Runs on Virtual Threads with an increased node limit (400k nodes) and an 8-second timeout, ensuring the server main thread **never freezes**.
- **Aerial Fallback & Extension:** Generates straight elevated paths if A* fails, or extends a partial ground path with a straight line to guide you all the way.
- **Persistent Storage:** Waypoints are stored in JSON format per-player and per-server in the server `config` directory.
- **Automatic Route Cleanup:** The trail gradually disappears behind you as you walk.

### 🎮 Commands

All commands are under the `/gps` root. The mod uses the Fabric command API, offering integrated auto-completion.

| Command | Description |
|---------|-------------|
| `/gps <name>` | Shortcut for `/gps go <name>`. Navigate instantly! |
| `/gps add <name>` | Saves your current position as a personal waypoint. |
| `/gps addcord <name> <coordinates>` | Saves specific coordinates as a personal waypoint. |
| `/gps list` | Lists personal and server waypoints with clickable `[Ir]`, `[Compartilhar]`, and `[Remover]` (OP only) buttons. |
| `/gps go <name> [scope]` | Calculates and displays the particle trail to the waypoint. |
| `/gps share <name> [scope]` | Shares the waypoint in public chat with an `[Adicionar]` button. |
| `/gps remove <name>` | Deletes a saved personal waypoint (with confirmation). |
| `/gps clear` | Clears the active visual trail from the screen. |
| `/gps server list` | Lists all server-wide waypoints. |
| `/gps server add <name>` | Saves current position as a server-wide waypoint (OP only). |
| `/gps server addcord <name> <coordinates>` | Saves coordinates as a server-wide waypoint (OP only). |
| `/gps server remove <name>` | Deletes a server-wide waypoint (OP only). |
| `/gps` or `/gps help` | Shows the in-game command help menu. |

### 🛠️ How to Install (Servers)

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) on your server compatible with Minecraft **26.2**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) (version compatible with 26.2).
3. Place the Fabric API `.jar` and the LumenGPS `.jar` in your server's `mods` folder.
4. Start the server and enjoy! Players can connect with vanilla clients and use all features.

### 💻 Development (Developers)

LumenGPS is built using modern modding tools from the Fabric ecosystem for Minecraft 26.2+ (*unobfuscated* code).

#### Prerequisites
- **Java 25** (JDK)
- Minecraft 26.2 (configured in Gradle)

#### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/MatheusFL99/LumenGPS.git
   cd MinecraftWaypointMod
   ```
2. Rebuild and generate VS Code / IntelliJ files:
   ```bash
   .\gradlew.bat genSources
   ```

#### Useful Scripts (Gradle)
- `.\gradlew.bat build` - Compiles the project and generates the `.jar` in `build/libs/`.
- `.\gradlew.bat runClient` - Starts a test Minecraft client with the mod already injected.

---

<h2 id="português">🇧🇷 Português</h2>

LumenGPS é um mod **server-side** para Minecraft Fabric que adiciona um sistema de rotas por GPS utilizando partículas brilhantes. O mod utiliza o algoritmo de *pathfinding* A* (A-Star) processado de forma assíncrona para guiar o jogador até waypoints salvos.

Como é um mod puramente **server-side**, **os jogadores não precisam instalar o mod em seus computadores** para ver a trilha de partículas e usar os comandos! Ele roda inteiramente no tick do servidor.

### 🌟 Funcionalidades

- **Totalmente Server-Side:** Não requer mod no cliente! Os jogadores conseguem ver a trilha brilhante e usar todos os comandos usando o cliente vanilla do Minecraft.
- **Pathfinding em Chunks Offline:** Calcula caminhos mesmo em chunks descarregados ou não explorados lendo diretamente os arquivos de região `.mca` do disco do servidor de forma assíncrona.
- **Navegação visual (Trilha Brilhante):** Partículas guiam você pelo caminho correto, contornando obstáculos, descendo precipícios e subindo blocos.
- **Waypoints do Servidor (Globais):** Operadores (OPs) podem registrar pontos globais (como `/spawn`, `/vila`) que qualquer jogador pode visualizar e navegar.
- **Prompts Interativos (Sem Emotes & Seguros):** Ações clicáveis no chat com confirmação de sobrescrita, confirmação de exclusão e resolução inteligente de conflitos quando um waypoint pessoal e um do servidor possuem o mesmo nome.
- **Pathfinding A\* otimizado:** Roda em Virtual Threads com limite aumentado (400k nós) e tempo limite de 8 segundos, garantindo que a thread principal do servidor **nunca trave**.
- **Fallback e Extensão Aérea:** Gera uma linha reta elevada se o A* falhar, ou estende um caminho terrestre parcial com uma linha reta aérea para guiar você até o destino final.
- **Armazenamento Persistente:** Waypoints salvos em formato JSON por jogador e por servidor na pasta `config` do servidor.
- **Limpeza automática de rota:** A trilha desaparece gradativamente atrás de você à medida que você caminha.

### 🎮 Comandos

Todos os comandos ficam sob a raiz `/gps`. O mod utiliza a API de comandos do Fabric, oferecendo auto-completar integrado.

| Comando | Descrição |
|---------|-----------|
| `/gps <nome>` | Atalho para `/gps go <nome>`. Navegue instantaneamente! |
| `/gps add <nome>` | Salva a sua posição atual como um waypoint pessoal. |
| `/gps addcord <nome> <coordenadas>` | Salva coordenadas específicas como um waypoint pessoal. |
| `/gps list` | Lista waypoints pessoais e de servidor com botões clicáveis `[Ir]`, `[Compartilhar]` e `[Remover]` (OP para globais). |
| `/gps go <nome> [escopo]` | Mostra a trilha de partículas até o waypoint. |
| `/gps share <nome> [escopo]` | Compartilha o waypoint no chat público com botão `[Adicionar]`. |
| `/gps remove <nome>` | Deleta um waypoint pessoal salvo (com confirmação). |
| `/gps clear` | Limpa a trilha visual atual da tela. |
| `/gps server list` | Lista todos os waypoints globais do servidor. |
| `/gps server add <nome>` | Salva a posição atual como waypoint global do servidor (apenas OP). |
| `/gps server addcord <nome> <coordenadas>` | Salva coordenadas como waypoint global do servidor (apenas OP). |
| `/gps server remove <nome>` | Deleta um waypoint global do servidor (apenas OP). |
| `/gps` ou `/gps help` | Exibe o menu de ajuda de comandos dentro do jogo. |

### 🛠️ Como Instalar (Servidores)

1. Instale o [Fabric Loader](https://fabricmc.net/use/installer/) no seu servidor compatível com Minecraft **26.2**.
2. Baixe o [Fabric API](https://modrinth.com/mod/fabric-api) (versão compatível com 26.2).
3. Coloque o `.jar` do Fabric API e o `.jar` do LumenGPS na pasta `mods` do seu servidor.
4. Inicie o servidor e divirta-se! Os jogadores podem se conectar com clientes vanilla e usar todos os recursos.

### 💻 Desenvolvimento (Programadores)

LumenGPS é construído utilizando as ferramentas modernas de modding do ecossistema Fabric para Minecraft 26.2+ (código *unobfuscated*).

#### Pré-requisitos
- **Java 25** (JDK)
- Minecraft 26.2 (configurado no Gradle)

#### Setup
1. Clone o repositório:
   ```bash
   git clone https://github.com/MatheusFL99/LumenGPS.git
   cd MinecraftWaypointMod
   ```
2. Reconstrua e gere os arquivos do VS Code / IntelliJ:
   ```bash
   .\gradlew.bat genSources
   ```

#### Scripts Úteis (Gradle)
- `.\gradlew.bat build` - Compila o projeto e gera o `.jar` em `build/libs/`.
- `.\gradlew.bat runClient` - Inicia um cliente Minecraft de testes com o mod já injetado.
