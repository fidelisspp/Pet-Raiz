# Roteiro da apresentação — Raiz Pet

Duração alvo: **12 a 15 minutos** de demonstração + perguntas. A ideia é contar uma história só:
um produtor novo entra na plataforma, vende, o cliente compra e o admin cuida de tudo, e no fim mostrar
que os serviços aguentam a queda um do outro.

## Antes de começar (no dia, 20 minutos antes)

- [ ] Feche Android Studio, IntelliJ, Spotify e abas do Chrome que não vai usar (o PC fica sem memória).
- [ ] Disco C: com pelo menos **5 GB livres** (o emulador não inicia com menos).
- [ ] MySQL no ar e `DB_PASSWORD` definida.
- [ ] Suba os serviços na ordem: usuários (8081) → produtos (8080) → pedidos (8082).
- [ ] `emulator -avd Medium_Phone -memory 1536 -no-snapshot` e espere o Android acalmar (1 a 2 minutos depois
      do boot; se aparecer "System UI isn't responding", toque em **Wait**).
- [ ] `adb reverse tcp:8081 tcp:8081`, `tcp:8080 tcp:8080`, `tcp:8082 tcp:8082`.
- [ ] Abra o app uma vez e veja a Loja carregar (o primeiro acesso a cada serviço é mais lento).
- [ ] Deixe no emulador um arquivo para o documento do produtor (qualquer PDF ou foto em Downloads).
- [ ] Abas do navegador prontas: `README.md` (diagrama) e o Swagger `http://localhost:8082/docs-petraiz.html`.
- [ ] Dados para digitar: CPF `529.982.247-25`, CNPJ `11.222.333/0001-81`, senha `123456`.

Contas (senha `123456`): admin `admin@petraiz.dev`, produtor `produtor@petraiz.dev`. O cliente e o produtor
novo são criados ao vivo.

## 1. Arquitetura (2 min) — README

- "Um app, três serviços, três bancos, um token." Mostre o diagrama.
- Cada serviço tem o próprio banco; um não lê tabela do outro, a referência é só o id.
- Só o service-usuarios emite o JWT; os outros só validam a assinatura e leem `id` e `role`.
- Chamadas entre serviços: HTTP síncrono nas rotas `/interno/**`, com a chave `X-Internal-Key`.
- Mostre o Swagger de um serviço e o `docs/API-CONTRATOS.md` (o contrato que o app segue).

## 2. Produtor novo se cadastra (2 min) — app, sem login

1. Login → **"É produtor? Cadastre seu negócio"**. Preencha, anexe o documento, envie.
2. Entre com o e-mail do produtor: abre a tela **"Cadastro em análise"** (o login devolve 403 e o app
   reconhece o motivo).

*O que falar:* o documento vai no mesmo pedido (multipart), aceita qualquer formato até 5 MB e fica no
service-usuarios.

## 3. Admin aprova (2 min) — `admin@petraiz.dev`

1. **Início:** os números do painel vêm de três serviços diferentes (pedidos, clientes, produtos).
2. **Aprovação → Pendentes:** abra o documento, toque em **Aprovar**.
3. (Opcional) Mostre que rejeitar pede um motivo e que o produtor vê esse motivo no login.

## 4. Produtor publica um produto (1 min) — produtor novo

1. Entre de novo com o produtor novo: agora abre a área do produtor.
2. **Meus produtos → Novo:** nome, preço, estoque, categoria e foto. Salve.
3. Saia e mostre o produto na **Loja**.

*O que falar:* o catálogo (produtos) pergunta ao service-usuarios quais produtores estão aprovados, numa
chamada só por requisição.

## 5. Cliente compra (3 min) — cadastro pelo app

1. **Cadastre-se** com o CPF de exemplo. Favorite um produto (coração).
2. Adicione **dois produtos de produtores diferentes** ao carrinho (o novo e um do Sítio Boa Vista).
3. **Finalizar:** cadastre o endereço, escolha o frete e Pix, confirme o pagamento.
4. **Pedidos:** o pedido mostra uma linha por produtor, cada uma com o seu status.

*O que falar:* no checkout, pedidos chama usuários (o cliente está suspenso?) e produtos (reserva de estoque
tudo ou nada, com `SELECT … FOR UPDATE`). Se gravar o pedido falhar depois da reserva, o estoque é estornado
(compensação).

## 6. Produtor atende (1 min) — produtor novo

1. **Pedidos recebidos:** ele vê **só a parte dele** do pedido.
2. **Avançar para "Em preparo"** e depois **"Enviado"**.
3. Volte ao cliente: em Meus pedidos, a linha desse produtor mudou e a do outro não.

## 7. Admin no dia a dia (2 min) — admin

1. **Pedidos:** filtro **Atrasados** (pago há mais de 3 dias e ainda não enviado). Mostre **Corrigir status**.
2. Tente **Cancelar** o pedido novo: o app explica que não dá, porque um produtor já enviou.
3. **Clientes:** a lista vem do service-usuarios e as compras ("N pedidos · R$ X") do service-pedidos.
   **Suspenda** o cliente e mostre que ele não consegue mais finalizar compra.
4. **Categorias** e **Produtos** rapidamente.

## 8. Um serviço cai (2 min) — o ponto alto

1. Pare o **service-pedidos** (Ctrl+C na janela dele).
2. No app: a **Loja** e o detalhe continuam funcionando (não dependem de pedidos).
3. Admin → **Início:** "Pedidos" e "Entregas atrasadas" mostram "—"; Clientes e Produtos continuam.
4. Admin → **Clientes:** a lista aparece, com "Compras indisponíveis no momento".
5. Suba o service-pedidos de novo e volte à tela: os números voltam.

*O que falar:* cada serviço responde 503 quando o outro cai, e o app mostra só aquela parte como
indisponível, em vez de quebrar a tela.

## 9. Testes e fechamento (1 min)

- Cada serviço tem testes de ponta a ponta com o banco `_test` e stubs HTTP no lugar dos outros serviços
  (`.\mvnw test`). Mostre um resultado já rodado em vez de rodar ao vivo (demora).
- O app tem teste de unidade das datas e o lint sem erros.

## Se algo der errado

| Problema | O que fazer |
|---|---|
| App: "Não foi possível conectar ao servidor" | `adb reverse --list`; se vazio, refaça os três `adb reverse` |
| 503 em tudo de um serviço | Ele caiu ou ainda está subindo: veja a janela dele |
| Emulador travado ("System UI isn't responding") | Toque em **Wait**; se repetir, **Close app** |
| Emulador não liga: "not enough disk space" | Libere espaço no C: (precisa de uns 5 GB) |
| Login do produtor novo abre "em análise" depois de aprovado | Saia e entre de novo |

## Depois do ensaio (e da apresentação)

O ensaio cria dados de verdade (cliente, produtor, produto, pedido). Para voltar ao estado limpo, restaure os
três bancos do backup `Documents/pet-raiz-backup-limpo/` (feito em 21/11, depois da limpeza dos dados de teste),
com os serviços parados:

```powershell
cd C:\Users\Tech2e\Documents\pet-raiz-backup-limpo
foreach ($db in "pet_usuarios_db","pet_produtos_db","pet_pedidos_db") {
  Get-Content "$db.sql" -Raw -Encoding UTF8 | mysql -u root -p --default-character-set=utf8mb4 $db
}
```

As fotos e documentos enviados no ensaio ficam nas pastas `uploads/` dos serviços; podem ser apagados à mão.
`Documents/pet-raiz-backup-antes-da-limpeza/` guarda os bancos como estavam antes da limpeza, por segurança.
