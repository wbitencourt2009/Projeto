# Player para LG (webOS)

Versão web do player para TVs LG com webOS 4.0 ou mais novo (modelos de 2018 em diante).
Mesmas telas do app Android: login Xtream/M3U, início com Ao vivo/Filmes/Séries, listas com
categorias, séries, busca geral, guia de programação, favoritos e assistidos recentemente.

## Gerar o pacote

```bash
npm ci
npm run package        # cria out/com.example.player_<versão>_all.ipk
```

Para ver no navegador com dados de exemplo: `npm run build` e abra `dist/index.html?demo=1`.
O controle é feito pelas setas, Enter (OK) e Esc/Backspace (Voltar).

## Instalar na TV (modo desenvolvedor)

1. Crie uma conta em https://webostv.developer.lge.com e, na TV, instale o app **Developer Mode** da LG Content Store.
2. Abra o Developer Mode na TV, entre com a conta, ative **Dev Mode Status** e reinicie a TV.
3. Abra o Developer Mode de novo e ative **Key Server**. Anote o IP da TV e a frase-senha (passphrase).
4. No computador (mesma rede da TV), instale as ferramentas: `npm install -g @webos-tools/cli`
5. Cadastre a TV: `ares-setup-device` → add → nome `tv`, IP da TV, porta 9922, usuário `prisoner`.
6. Pegue a chave: `ares-novacom --device tv --getkey` (digite a passphrase).
7. Instale: `ares-install --device tv com.example.player_0.3.0_all.ipk`

O app aparece na lista de apps da TV como **Player**. O modo desenvolvedor expira em 50 horas;
abra o app Developer Mode e use **Extend** para renovar antes de expirar.
Alternativa gráfica ao passo 4-7: o programa gratuito *webOS Dev Manager* instala o `.ipk` com poucos cliques.
