# LumenGPS 🗺️✨

LumenGPS é um mod **client-side** para Minecraft Fabric que adiciona um sistema de rotas por GPS utilizando partículas brilhantes. O mod utiliza o algoritmo de *pathfinding* A* (A-Star) processado de forma assíncrona para guiar o jogador até waypoints salvos, sem congelar o jogo.

Como é um mod puramente *client-side*, **funciona em qualquer servidor** (Vanilla, Spigot, Paper, etc.) sem precisar que o servidor tenha o mod instalado.

---

## 🌟 Funcionalidades

- **Client-side absoluto:** Salve pontos e trace rotas localmente. O servidor não sabe que você está usando.
- **Navegação visual (Trilha Brilhante):** Partículas mágicas flutuantes guiam você pelo caminho correto, contornando obstáculos, descendo precipícios e subindo blocos.
- **Pathfinding A\* otimizado:** O cálculo de rotas roda em uma thread secundária (Virtual Threads) e tem um limite de segurança (100k nós) e tempo (3 segundos), garantindo que o jogo **nunca trave**, mesmo para destinos inalcançáveis.
- **Fallback Aéreo (Crow-fly):** Se o caminho estiver bloqueado (ex: montanha intransponível) ou muito distante para o algoritmo processar no tempo limite, o mod automaticamente cria uma rota reta e elevada apontando diretamente para o destino.
- **Armazenamento Persistente:** Waypoints são salvos em um arquivo JSON local na pasta de configurações do seu Minecraft.
- **Limpeza automática de rota:** A trilha desaparece gradativamente atrás de você à medida que você caminha por ela.

---

## 🎮 Comandos

Todos os comandos ficam sob a raiz `/gps`. O mod utiliza a API de comandos do Fabric, oferecendo auto-completar integrado.

| Comando | Descrição |
|---------|-----------|
| `/gps add <nome>` | Salva a sua posição atual como um waypoint com o nome fornecido. |
| `/gps list` | Lista no chat todos os waypoints que você salvou. |
| `/gps go <nome>` | Calcula e exibe a trilha de partículas guiando você da sua posição atual até o waypoint. |
| `/gps clear` | Limpa a trilha visual atual da tela. |

---

## 🛠️ Como Instalar (Jogadores)

1. Instale o [Fabric Loader](https://fabricmc.net/use/installer/) para Minecraft **26.1.x**.
2. Baixe o [Fabric API](https://modrinth.com/mod/fabric-api) (versão compatível com 26.1).
3. Coloque o `.jar` do Fabric API e o `.jar` do LumenGPS na sua pasta `mods` (`%appdata%/.minecraft/mods`).
4. Abra o jogo e use `/gps`!

---

## 💻 Desenvolvimento (Programadores)

LumenGPS é construído utilizando as ferramentas modernas de modding do ecossistema Fabric para Minecraft 26.1+ (código *unobfuscated*).

### Pré-requisitos
- **Java 25** (JDK)
- Minecraft 26.1.2 (configurado no Gradle)

### Setup
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

### Scripts Úteis (Gradle)
- `.\gradlew.bat build` - Compila o projeto e gera o `.jar` em `build/libs/`.
- `.\gradlew.bat runClient` - Inicia um cliente Minecraft de testes com o mod já injetado.

> **Dica para VS Code:** O projeto contém uma task `LumenGPS: Watch` (Ctrl+Shift+P > Run Task) que fica rodando em background e reconstrói o mod sempre que você salva um arquivo `.java`.

---

## 🏗️ Arquitetura Interna

O mod é dividido em quatro pilares principais, localizados em `com.lumengps`:

1. **`command/GpsCommand`**: Registra os subcomandos cliente.
2. **`data/WaypointManager`**: Singleton que salva/carrega o mapeamento de Nome → Coodenadas em disco (`waypoints.json`).
3. **`pathfinding/Pathfinder`**: O cérebro das rotas.
   - Roda A* em uma *Virtual Thread*.
   - Utiliza as colisões reais dos blocos (VoxelShapes via `BlockUtil.java`) para entender se o jogador consegue andar ali. Suporta slabs, neve, escadas, e até rotas nadando (água).
   - Possui heurísticas para descidas (até -3Y) e subidas (até +2Y).
   - Contém fallback para uma rota reta caso o limite computacional seja atingido.
4. **`renderer/GpsRenderer`**: Singleton executado a cada tick do cliente. Avalia a lista de pontos densos (`Vec3`) gerados pelo Pathfinder e emite partículas do tipo `GLOW` nos pontos próximos ao jogador (Raio de 30 blocos), fazendo *culling* dos pontos que o jogador já ultrapassou.
