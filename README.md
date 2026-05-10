# LumenGPS 🗺️✨

*Read this in other languages: [English](#english), [Português](#português)*

---

<h2 id="english">🇬🇧 English</h2>

LumenGPS is a **client-side** mod for Minecraft Fabric that adds a GPS routing system using glowing particles. The mod uses an asynchronous A* (A-Star) pathfinding algorithm to guide players to saved waypoints without freezing the game.

Since it is purely a *client-side* mod, **it works on any server** (Vanilla, Spigot, Paper, etc.) without requiring the server to have the mod installed.

### 🌟 Features

- **Absolute Client-Side:** Save points and trace routes locally. The server doesn't know you are using it.
- **Visual Navigation (Glowing Trail):** Magical floating particles guide you along the correct path, avoiding obstacles, descending cliffs, and climbing blocks.
- **Optimized A\* Pathfinding:** Route calculation runs on a secondary thread (Virtual Threads) and has a safety node limit (100k nodes) and a time limit (3 seconds), ensuring the game **never freezes**, even for unreachable destinations.
- **Aerial Fallback (Crow-fly):** If the path is blocked (e.g., an impassable mountain) or too far to process within the time limit, the mod automatically creates a straight, elevated route pointing directly to the destination.
- **Persistent Storage:** Waypoints are saved in a local JSON file in your Minecraft config folder.
- **Automatic Route Cleanup:** The trail gradually disappears behind you as you walk along it.

### 🎮 Commands

All commands are under the `/gps` root. The mod uses the Fabric command API, offering integrated auto-completion.

| Command | Description |
|---------|-------------|
| `/gps add <name>` | Saves your current position as a waypoint with the given name. |
| `/gps list` | Lists all your saved waypoints in the chat. |
| `/gps go <name>` | Calculates and displays the particle trail guiding you from your current position to the waypoint. |
| `/gps clear` | Clears the current visual trail from the screen. |

### 🛠️ How to Install (Players)

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.1.x**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) (version compatible with 26.1).
3. Place the Fabric API `.jar` and the LumenGPS `.jar` in your `mods` folder (`%appdata%/.minecraft/mods`).
4. Open the game and use `/gps`!

### 💻 Development (Developers)

LumenGPS is built using modern modding tools from the Fabric ecosystem for Minecraft 26.1+ (*unobfuscated* code).

#### Prerequisites
- **Java 25** (JDK)
- Minecraft 26.1.2 (configured in Gradle)

#### Setup
1. Clone the repository:
   ```bash
   git clone <repo-url>
   cd MinecraftWaypointMod
   ```
2. Rebuild and generate VS Code / IntelliJ files:
   The project already includes native configurations to compile using the Gradle Wrapper.
   ```bash
   .\gradlew.bat genSources
   ```

#### Useful Scripts (Gradle)
- `.\gradlew.bat build` - Compiles the project and generates the `.jar` in `build/libs/`.
- `.\gradlew.bat runClient` - Starts a test Minecraft client with the mod already injected.

> **VS Code Tip:** The project contains a `LumenGPS: Watch` task (Ctrl+Shift+P > Run Task) that runs in the background and automatically rebuilds the mod whenever you save a `.java` file.

---

<h2 id="português">🇧🇷 Português</h2>

LumenGPS é um mod **client-side** para Minecraft Fabric que adiciona um sistema de rotas por GPS utilizando partículas brilhantes. O mod utiliza o algoritmo de *pathfinding* A* (A-Star) processado de forma assíncrona para guiar o jogador até waypoints salvos, sem congelar o jogo.

Como é um mod puramente *client-side*, **funciona em qualquer servidor** (Vanilla, Spigot, Paper, etc.) sem precisar que o servidor tenha o mod instalado.

### 🌟 Funcionalidades

- **Client-side absoluto:** Salve pontos e trace rotas localmente. O servidor não sabe que você está usando.
- **Navegação visual (Trilha Brilhante):** Partículas mágicas flutuantes guiam você pelo caminho correto, contornando obstáculos, descendo precipícios e subindo blocos.
- **Pathfinding A\* otimizado:** O cálculo de rotas roda em uma thread secundária (Virtual Threads) e tem um limite de segurança (100k nós) e tempo (3 segundos), garantindo que o jogo **nunca trave**, mesmo para destinos inalcançáveis.
- **Fallback Aéreo (Crow-fly):** Se o caminho estiver bloqueado (ex: montanha intransponível) ou muito distante para o algoritmo processar no tempo limite, o mod automaticamente cria uma rota reta e elevada apontando diretamente para o destino.
- **Armazenamento Persistente:** Waypoints são salvos em um arquivo JSON local na pasta de configurações do seu Minecraft.
- **Limpeza automática de rota:** A trilha desaparece gradativamente atrás de você à medida que você caminha por ela.

### 🎮 Comandos

Todos os comandos ficam sob a raiz `/gps`. O mod utiliza a API de comandos do Fabric, oferecendo auto-completar integrado.

| Comando | Descrição |
|---------|-----------|
| `/gps add <nome>` | Salva a sua posição atual como um waypoint com o nome fornecido. |
| `/gps list` | Lista no chat todos os waypoints que você salvou. |
| `/gps go <nome>` | Calcula e exibe a trilha de partículas guiando você da sua posição atual até o waypoint. |
| `/gps clear` | Limpa a trilha visual atual da tela. |

### 🛠️ Como Instalar (Jogadores)

1. Instale o [Fabric Loader](https://fabricmc.net/use/installer/) para Minecraft **26.1.x**.
2. Baixe o [Fabric API](https://modrinth.com/mod/fabric-api) (versão compatível com 26.1).
3. Coloque o `.jar` do Fabric API e o `.jar` do LumenGPS na sua pasta `mods` (`%appdata%/.minecraft/mods`).
4. Abra o jogo e use `/gps`!

### 💻 Desenvolvimento (Programadores)

LumenGPS é construído utilizando as ferramentas modernas de modding do ecossistema Fabric para Minecraft 26.1+ (código *unobfuscated*).

#### Pré-requisitos
- **Java 25** (JDK)
- Minecraft 26.1.2 (configurado no Gradle)

#### Setup
1. Clone o repositório:
   ```bash
   git clone <url-do-repo>
   cd MinecraftWaypointMod
   ```
2. Reconstrua e gere os arquivos do VS Code / IntelliJ:
   O projeto já inclui as configurações nativas para compilar usando o Gradle Wrapper.
   ```bash
   .\gradlew.bat genSources
   ```

#### Scripts Úteis (Gradle)
- `.\gradlew.bat build` - Compila o projeto e gera o `.jar` em `build/libs/`.
- `.\gradlew.bat runClient` - Inicia um cliente Minecraft de testes com o mod já injetado.

> **Dica para VS Code:** O projeto contém uma task `LumenGPS: Watch` (Ctrl+Shift+P > Run Task) que fica rodando em background e reconstrói o mod sempre que você salva um arquivo `.java`.
