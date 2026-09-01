# Lua Client Mobile

Cliente/launcher Android para Minecraft Bedrock, criado por **Kauan** e baseado no projeto open-source [LeviLaunchroid](https://github.com/LiteLDev/LeviLaunchroid).

- Android 9 ou superior (API 28)
- ARM64-v8a
- APK único, sem DLL ou instalador para Windows
- Importação de uma cópia oficial do Minecraft pertencente ao usuário
- Seleção e isolamento de versões compatíveis
- Xbox Live preservado pela base do launcher
- Núcleo nativo `libLuaClient.so`
- Menu de módulos adaptado para toque

## Estado da v0.1.0

O MVP mantém o fluxo do LeviLaunchroid para importar e iniciar o Minecraft oficial. O Lua Client acrescenta identidade própria, núcleo ARM64, logs locais exportáveis e usa os módulos seguros já disponíveis na base:

- FPS e CPS/toques, ativados por padrão
- Zoom visual local
- ocultação do HUD
- Quick Drop
- perspectiva rápida
- Snaplook
- cursor virtual e giroscópio
- controles móveis configuráveis
- botões adicionais e atalhos da hotbar
- pesquisa, favoritos, configurações por módulo e editor de HUD

Consulte [docs/LUA_CLIENT_STATUS.md](docs/LUA_CLIENT_STATUS.md) para saber o que está funcional, parcial ou indisponível. O aplicativo não simula uma função quando ela ainda não foi implementada com segurança.

## Minecraft e conta Microsoft

Este repositório **não distribui o APK do Minecraft**. É necessário possuir uma cópia oficial do Minecraft Bedrock para Android. O launcher não pede nem coleta senhas da Microsoft. O login é executado pelos fluxos compatíveis preservados do LeviLaunchroid/MinecraftAuth.

## Compilação

Requisitos principais:

- JDK 21
- Android SDK 36
- Android NDK r28c
- Git com submódulos

```bash
git clone --recurse-submodules https://github.com/KauanGitpy/LuaClient-Android.git
cd LuaClient-Android
./gradlew assembleDebug
```

O GitHub Actions compila no Linux, confirma a presença de `lib/arm64-v8a/libLuaClient.so`, verifica a assinatura/integridade do APK e publica o arquivo com o padrão:

`LuaClient-Mobile-vX.X.X-arm64.apk`

## Segurança e limitações

O Lua Client Mobile não inclui funções para burlar anticheat, modificar pacotes maliciosamente ou conceder vantagens injustas. Recursos que dependem de estruturas internas do Minecraft só serão habilitados após validação específica por versão.

Os relatórios de falha remotos ficam desativados por padrão. O log próprio do Lua Client é local, limitado e não registra senhas, tokens ou dados da conta. Consulte [PRIVACY.md](PRIVACY.md).

## Autor e contato

- Criador: **Kauan**
- YouTube: [KauanPlays](https://www.youtube.com/@KauanPlays)
- Discord: **kauanbl**

## Licença e créditos

Este trabalho derivado é distribuído sob a licença Apache-2.0. A licença original foi preservada em [LICENSE](LICENSE).

Créditos obrigatórios e dependências:

- [LeviLaunchroid](https://github.com/LiteLDev/LeviLaunchroid), mantido pela LiteLDev/LeviMC e seus contribuidores
- `preloader-android`, `libHttpClient`, PojavControls e demais dependências listadas em [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

Lua Client Mobile não é afiliado à Mojang, Microsoft ou aos clientes Flarial e Atlas. Flarial e Atlas foram usados somente como referência geral de organização visual e funcional; nenhum arquivo ou código fechado desses projetos foi copiado.
