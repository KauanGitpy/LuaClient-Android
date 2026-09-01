# Privacy

Lua Client Mobile does not request, collect, or transmit Microsoft passwords. Authentication remains in the compatible Microsoft/Xbox flow inherited from LeviLaunchroid and MinecraftAuth.

The Lua Client diagnostic log is stored locally under the application data directory. It contains only startup state, Android/ABI information, native-core state, module lifecycle events, rendering startup events, and failure class/messages. It is exported only after the user taps **Export Lua Client logs** and chooses a destination through Android's share sheet.

The Lua Client log must not contain access tokens, refresh tokens, passwords, chat messages, world content, or account identifiers.

Remote crash-report upload inherited from the upstream base is disabled by default. Users may enable the optional upstream setting manually.
