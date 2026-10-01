/**
 * ==========================================================================
 * Assistente de Vendas - Unitrama Inteligência de Vendas (Oficial Sankhya)
 * Injeção Flutuante 100% Nativa, Autônoma e Não-Bloqueante
 * ==========================================================================
 */

(function () {
    // 1. Limpeza Segura de Instâncias Anteriores (Singleton Estrito)
    // Mantém APENAS a instância mais recente no DOM e NUNCA remove nós do AngularJS/UI-Bootstrap
    try {
        var allRoots = document.querySelectorAll("#assistente-root");
        if (allRoots.length > 1) {
            for (var k = 0; k < allRoots.length - 1; k++) {
                try {
                    allRoots[k].style.display = "none";
                    allRoots[k].parentNode.removeChild(allRoots[k]);
                } catch (eClean) {}
            }
        }
    } catch (eRoots) {}

    var asstData = window.__asstData || {};
    var itensSugestoes = [];
    var itensSugestoesMap = {};
    var nuNotaAtual = (typeof asstData.nunota !== "undefined") ? asstData.nunota : ((typeof nunota !== "undefined") ? nunota : 0);
    var currentActionID = (typeof asstData.actionID !== "undefined") ? asstData.actionID : ((typeof actionID !== "undefined") ? actionID : 0);

    function indexarSugestoesMap() {
        itensSugestoesMap = {};
        for (var i = 0; i < itensSugestoes.length; i++) {
            var it = itensSugestoes[i];
            if (it && it.codProd) {
                var vlrNum = Number(it.vlrVenda) || 0;
                var mNum = (typeof it.margemSugerida !== "undefined" && it.margemSugerida !== null) ? Number(it.margemSugerida) : 25;
                it.margemSugerida = mNum;
                if (!it.fatorK || Number(it.fatorK) <= 0) {
                    if (vlrNum > 0) {
                        it.fatorK = vlrNum * (1 - (mNum / 100));
                    } else {
                        it.fatorK = 0;
                    }
                }
                itensSugestoesMap[it.codProd] = it;
            }
        }
    }

    try {
        var rawSug = (typeof asstData.sugestoes !== "undefined") ? asstData.sugestoes : ((typeof sugestoes !== "undefined") ? sugestoes : null);
        if (rawSug) {
            itensSugestoes = (typeof rawSug === "string") ? JSON.parse(rawSug) : rawSug;
            indexarSugestoesMap();
        }
    } catch (e) {
        console.error("[AssistenteVendas Unitrama] Erro ao fazer parse de sugestoes:", e);
    }

    console.log("[AssistenteVendas Unitrama] Inicializando para Pedido #" + nuNotaAtual + ". Sugestoes: " + itensSugestoes.length);

    // --------------------------------------------------------------------------
    // Salvaguardas Nativas de Ancoragem de Pedido na Central de Vendas (SankhyaW)
    // Evita que o ActionButtonService execute dataset.refresh() e pule para a linha 0 (ex: NUNOTA 10)
    // --------------------------------------------------------------------------

    // Coleta recursivamente a janela atual, parent, top e todos os iframes do SankhyaW
    function coletarTodasJanelas() {
        var janelas = [window];
        try { if (window.parent && window.parent !== window && janelas.indexOf(window.parent) === -1) janelas.push(window.parent); } catch (eWp) {}
        try { if (window.top && window.top !== window && janelas.indexOf(window.top) === -1) janelas.push(window.top); } catch (eWt) {}

        for (var i = 0; i < janelas.length; i++) {
            try {
                var d = janelas[i].document;
                if (!d) continue;
                var iframes = d.querySelectorAll("iframe");
                for (var f = 0; f < iframes.length; f++) {
                    try {
                        var cw = iframes[f].contentWindow;
                        if (cw && janelas.indexOf(cw) === -1) {
                            janelas.push(cw);
                        }
                    } catch (eIf) {}
                }
            } catch (eDoc) {}
        }
        return janelas;
    }

    // Camada 1: Neutraliza o callback registrado em actionButtonCache
    function neutralizarRefreshAcao() {
        try {
            var ng = window.angular || (window.parent && window.parent.angular) || (window.top && window.top.angular);
            if (!ng) return;

            var winList = coletarTodasJanelas();

            for (var w = 0; w < winList.length; w++) {
                var win = winList[w];
                if (!win || !win.document || !win.document.body) continue;
                var el = win.angular ? win.angular.element(win.document.body) : ng.element(win.document.body);
                var inj = el.injector ? el.injector() : null;
                if (inj && inj.has && inj.has("$cacheFactory")) {
                    var $cacheFactory = inj.get("$cacheFactory");
                    var btnCache = $cacheFactory.get("actionButtonCache");
                    if (btnCache) {
                        if (currentActionID) {
                            btnCache.remove(currentActionID);
                            btnCache.remove(String(currentActionID));
                            btnCache.remove(Number(currentActionID));
                        }
                        btnCache.removeAll();
                        console.log("[AssistenteVendas] actionButtonCache limpo com sucesso.");
                    }
                }
            }
        } catch (eCache) {
            console.warn("[AssistenteVendas] Aviso ao neutralizar actionButtonCache:", eCache);
        }
    }

    // Camada 2: Protege recordsReloader no $rootScope para que 'ALL' vire 'CURRENT'
    function protegerRecordsReloader() {
        try {
            var ng = window.angular || (window.parent && window.parent.angular) || (window.top && window.top.angular);
            if (!ng) return;

            var winList = coletarTodasJanelas();

            for (var w = 0; w < winList.length; w++) {
                var win = winList[w];
                if (!win || !win.document || !win.document.body) continue;
                var el = win.angular ? win.angular.element(win.document.body) : ng.element(win.document.body);
                var sc = el.scope ? el.scope() : null;
                var root = sc ? sc.$root : null;
                if (root && root.context && typeof root.context.recordsReloader === "function") {
                    if (!root.context.__asstOrigReloader) {
                        root.context.__asstOrigReloader = root.context.recordsReloader;
                        root.context.recordsReloader = function (refreshType) {
                            console.log("[AssistenteVendas] recordsReloader interceptado com tipo:", refreshType);
                            if (refreshType === "ALL" || refreshType === "MASTER" || refreshType === "PARENT") {
                                console.log("[AssistenteVendas] Tipo " + refreshType + " convertido em CURRENT para preservar Pedido #" + nuNotaAtual);
                                return root.context.__asstOrigReloader.call(root.context, "CURRENT");
                            }
                            return root.context.__asstOrigReloader.apply(root.context, arguments);
                        };
                    }
                }
            }
        } catch (eReloader) {
            console.warn("[AssistenteVendas] Aviso ao proteger recordsReloader:", eReloader);
        }
    }

    // Camada 3: Localiza todos os datasets do cabeçalho da nota e reancora se houver salto
    function encontrarDatasetsCabecalho() {
        var datasets = [];
        var ng = window.angular || (window.parent && window.parent.angular) || (window.top && window.top.angular);
        if (!ng) return datasets;

        var winList = coletarTodasJanelas();

        for (var w = 0; w < winList.length; w++) {
            var win = winList[w];
            if (!win || !win.document) continue;
            var d = win.document;

            // A. Pelo elemento com ID datasetMaster
            var elMaster = d.getElementById("datasetMaster");
            if (elMaster) {
                var ctrl = ng.element(elMaster).controller("skDataset");
                if (ctrl && datasets.indexOf(ctrl) === -1) datasets.push(ctrl);
                var sc = ng.element(elMaster).scope();
                if (sc && sc.datasetMaster && datasets.indexOf(sc.datasetMaster) === -1) datasets.push(sc.datasetMaster);
                if (sc && sc.dataset && datasets.indexOf(sc.dataset) === -1) datasets.push(sc.dataset);
            }

            // B. Por componentes sk-central-notas-html5
            var centralEls = d.querySelectorAll("sk-central-notas-html5, [ng-controller*='CentralNotasController']");
            for (var c = 0; c < centralEls.length; c++) {
                var cCtrl = ng.element(centralEls[c]).controller("skCentralNotasHtml5") || ng.element(centralEls[c]).controller();
                if (cCtrl && typeof cCtrl.getDataSet === "function") {
                    var ds = cCtrl.getDataSet();
                    if (ds && datasets.indexOf(ds) === -1) datasets.push(ds);
                }
            }

            // C. Por qualquer sk-dataset com entity-name="CabecalhoNota"
            var entityEls = d.querySelectorAll("sk-dataset[entity-name='CabecalhoNota']");
            for (var e = 0; e < entityEls.length; e++) {
                var eCtrl = ng.element(entityEls[e]).controller("skDataset");
                if (eCtrl && datasets.indexOf(eCtrl) === -1) datasets.push(eCtrl);
            }
        }
        return datasets;
    }

    function verificarEAncorarDataset(ds, origem) {
        try {
            if (!ds) return;
            var nunotaDs = null;
            if (typeof ds.getFieldValueAsNumber === "function") {
                nunotaDs = ds.getFieldValueAsNumber("NUNOTA");
            } else if (typeof ds.getFieldValue === "function") {
                nunotaDs = ds.getFieldValue("NUNOTA");
            } else if (typeof ds.getCurrentRow === "function" && ds.getCurrentRow()) {
                var row = ds.getCurrentRow();
                nunotaDs = row.NUNOTA || row[0];
            }

            if (nunotaDs && Number(nunotaDs) !== Number(nuNotaAtual) && Number(nuNotaAtual) > 0) {
                console.warn("[AssistenteVendas] DETECTADO DESVIO DE REGISTRO (" + origem + "): Tela em NUNOTA=" + nunotaDs + " != " + nuNotaAtual + ". Reancorando...");
                if (typeof ds.locateByPk === "function") {
                    ds.locateByPk({ NUNOTA: nuNotaAtual });
                } else if (typeof ds.findRecordIndexByPk === "function" && typeof ds.gotoRow === "function") {
                    var idx = ds.findRecordIndexByPk({ NUNOTA: nuNotaAtual });
                    if (idx > -1) {
                        ds.gotoRow(idx);
                    }
                }
            }
        } catch (eDs) {
            console.warn("[AssistenteVendas] Erro ao verificar e ancorar dataset:", eDs);
        }
    }

    function ancorarPedidoAtual() {
        if (!nuNotaAtual || nuNotaAtual <= 0) return;
        try {
            var list = encontrarDatasetsCabecalho();
            for (var i = 0; i < list.length; i++) {
                verificarEAncorarDataset(list[i], "datasetCabecalho[" + i + "]");
            }
        } catch (e) {
            console.warn("[AssistenteVendas] Aviso ao ancorar pedido atual:", e);
        }
    }

    // Execução síncrona imediata das 3 camadas de salvaguarda
    neutralizarRefreshAcao();
    protegerRecordsReloader();
    ancorarPedidoAtual();

    // 2. Formatação Monetária e Numérica Brasileira Padrão (ex: 1.000,00)
    function parseNumeroBR(val) {
        if (val === null || val === undefined) return 0;
        if (typeof val === "number") return val;
        var s = String(val).trim();
        if (s.indexOf(",") > -1) {
            s = s.replace(/\./g, "").replace(",", ".");
        }
        var num = parseFloat(s);
        return isNaN(num) ? 0 : num;
    }

    function formatarNumeroBR(val, decimais) {
        if (val === null || val === undefined || isNaN(val)) return "0,00";
        decimais = (decimais !== undefined) ? decimais : 2;
        var num = Number(val);
        try {
            return num.toLocaleString("pt-BR", { minimumFractionDigits: decimais, maximumFractionDigits: decimais });
        } catch (e) {
            return num.toFixed(decimais).replace(".", ",");
        }
    }

    function formatarMoeda(val) {
        return formatarNumeroBR(val, 2);
    }

    // 3. Renderização dos Dados na Interface
    function renderizarDados() {
        var total = itensSugestoes.length;

        // Atualizar texto do Balãozinho
        var elSub = document.getElementById("asst-balloon-subtitle");
        if (elSub) {
            if (total === 0) {
                elSub.textContent = "Sem novas sugestões";
            } else if (total === 1) {
                elSub.textContent = "Ofereça este 1 item";
            } else {
                elSub.textContent = "Ofereça estes " + total + " itens";
            }
        }

        // Renderizar Cards no Drawer
        var container = document.getElementById("asst-drawer-cards");
        var emptyEl = document.getElementById("asst-drawer-empty");

        if (container) {
            container.innerHTML = "";
            if (total === 0) {
                if (emptyEl) emptyEl.style.display = "block";
            } else {
                if (emptyEl) emptyEl.style.display = "none";
                for (var i = 0; i < itensSugestoes.length; i++) {
                    container.appendChild(criarCardProduto(itensSugestoes[i]));
                }
            }
        }
    }

    // 4. Construtor Seguro de Card via DOM com Imagem BLOB Nativa TGFPRO e Preço Formatado
    function criarCardProduto(item) {
        var card = document.createElement("div");
        card.className = "asst-card";
        card.id = "asst-card-" + item.codProd;

        var vlr = formatarMoeda(item.vlrVenda);
        var descr = item.descrProd || "PRODUTO RECOMENDADO";
        var cod = item.codProd || "";
        var hint = item.hintTexto || item.motivo || "Quem comprou os itens deste pedido também costuma levar este produto.";

        // Endpoint Oficial do Sankhya MGE para Imagens BLOB do Produto (TGFPRO.IMAGEM)
        var imgSrc = "/mge/Produto@IMAGEM@CODPROD=" + cod + ".dbimage";
        var fallbackSvg = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI2NCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiMxMGI5ODEiIHN0cm9rZS13aWR0aD0iMS41IiBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiPjxwYXRoIGQ9Ik02IDJMMyA2djE0YTIgMiAwIDAgMCAyIDJoMTRhMiAyIDAgMCAwIDItMlY2bC0zLTR6Ii8+PGxpbmUgeDE9IjMiIHkxPSI2IiB4Mj0iMjEiIHkyPSI2Ii8+PHBhdGggZD0iTTE2IDEwYTQgNCAwIDAgMS04IDAiLz48L3N2Zz4=";

        // Tooltip explicativo ao passar o mouse
        var tooltip = document.createElement("div");
        tooltip.className = "asst-tooltip";
        tooltip.textContent = hint;
        card.appendChild(tooltip);

        // Miniatura do Produto (Foto BLOB TGFPRO com fallback elegante)
        var thumb = document.createElement("div");
        thumb.className = "asst-card-thumb";
        var img = document.createElement("img");
        img.src = imgSrc;
        img.alt = descr;
        img.onerror = function () {
            this.onerror = null;
            this.src = fallbackSvg;
        };
        thumb.appendChild(img);
        card.appendChild(thumb);

        // Detalhes (Nome, Código, Preço, Stepper, Botão Adicionar)
        var content = document.createElement("div");
        content.className = "asst-card-content";

        var info = document.createElement("div");
        var name = document.createElement("div");
        name.className = "asst-card-name";
        name.title = descr;
        name.textContent = descr;
        var code = document.createElement("div");
        code.className = "asst-card-code";
        code.textContent = "Código: " + cod;
        info.appendChild(name);
        info.appendChild(code);
        content.appendChild(info);

        // Bloco de Precificação e Margem Unitrama (Negociação Interativa)
        var pricingRow = document.createElement("div");
        pricingRow.className = "asst-pricing-row";

        // Coluna 1: Preço Unitário
        var colPreco = document.createElement("div");
        colPreco.className = "asst-pricing-col";

        var lblPreco = document.createElement("label");
        lblPreco.className = "asst-pricing-label";
        lblPreco.textContent = "Preço (R$)";
        colPreco.appendChild(lblPreco);

        var wrapPreco = document.createElement("div");
        wrapPreco.className = "asst-input-wrapper";

        var pfxPreco = document.createElement("span");
        pfxPreco.className = "asst-input-prefix";
        pfxPreco.textContent = "R$";
        wrapPreco.appendChild(pfxPreco);

        var inputPreco = document.createElement("input");
        inputPreco.type = "text";
        inputPreco.className = "asst-input-preco";
        inputPreco.id = "asst-preco-" + cod;
        inputPreco.value = formatarNumeroBR(item.vlrVenda, 2);
        inputPreco.oninput = function () {
            var fn = window.asstAlterarPreco || (window.top && window.top.asstAlterarPreco);
            if (fn) fn(cod);
        };
        inputPreco.onblur = function () {
            var p = parseNumeroBR(this.value);
            this.value = formatarNumeroBR(p, 2);
        };
        wrapPreco.appendChild(inputPreco);
        colPreco.appendChild(wrapPreco);
        pricingRow.appendChild(colPreco);

        // Coluna 2: Margem Alvo (%)
        var colMargem = document.createElement("div");
        colMargem.className = "asst-pricing-col";

        var lblMargem = document.createElement("label");
        lblMargem.className = "asst-pricing-label";
        lblMargem.textContent = "Margem (%)";
        colMargem.appendChild(lblMargem);

        var wrapMargem = document.createElement("div");
        wrapMargem.className = "asst-input-wrapper";

        var sfxMargem = document.createElement("span");
        sfxMargem.className = "asst-input-suffix";
        sfxMargem.textContent = "%";

        var inputMargem = document.createElement("input");
        inputMargem.type = "text";
        inputMargem.className = "asst-input-margem";
        inputMargem.id = "asst-margem-" + cod;
        var margemInicial = (typeof item.margemSugerida !== "undefined" && item.margemSugerida !== null) ? Number(item.margemSugerida) : 25;
        inputMargem.value = formatarNumeroBR(margemInicial, 2);
        if (margemInicial < 0) {
            inputMargem.classList.add("asst-margem-negativa");
        }
        inputMargem.oninput = function () {
            var fn = window.asstAlterarMargem || (window.top && window.top.asstAlterarMargem);
            if (fn) fn(cod);
        };
        inputMargem.onblur = function () {
            var m = parseNumeroBR(this.value);
            this.value = formatarNumeroBR(m, 2);
        };
        wrapMargem.appendChild(inputMargem);
        wrapMargem.appendChild(sfxMargem);
        colMargem.appendChild(wrapMargem);
        pricingRow.appendChild(colMargem);

        content.appendChild(pricingRow);

        var controls = document.createElement("div");
        controls.className = "asst-card-controls";

        var stepper = document.createElement("div");
        stepper.className = "asst-stepper";

        var btnMinus = document.createElement("button");
        btnMinus.type = "button";
        btnMinus.className = "asst-step-btn";
        btnMinus.textContent = "-";
        btnMinus.onclick = function () { asstAlterarQtd(cod, -1); };

        var input = document.createElement("input");
        input.type = "text";
        input.className = "asst-step-input";
        input.id = "asst-qty-" + cod;
        input.value = "1";
        input.readOnly = true;

        var btnPlus = document.createElement("button");
        btnPlus.type = "button";
        btnPlus.className = "asst-step-btn";
        btnPlus.textContent = "+";
        btnPlus.onclick = function () { asstAlterarQtd(cod, 1); };

        stepper.appendChild(btnMinus);
        stepper.appendChild(input);
        stepper.appendChild(btnPlus);
        controls.appendChild(stepper);

        var btnAdd = document.createElement("button");
        btnAdd.type = "button";
        btnAdd.className = "asst-btn-add";
        btnAdd.id = "asst-btn-" + cod;
        btnAdd.textContent = "Adicionar";
        btnAdd.onclick = function () { asstAdicionarAoPedido(cod); };
        controls.appendChild(btnAdd);

        content.appendChild(controls);
        card.appendChild(content);

        return card;
    }

    // 5. Exposição de Funções de Controle Globais (window, top, parent)
    var targets = [window];
    try { if (window.top && window.top !== window) targets.push(window.top); } catch (e) {}
    try { if (window.parent && window.parent !== window) targets.push(window.parent); } catch (e) {}

    function exp(nome, fn) {
        for (var i = 0; i < targets.length; i++) {
            try { targets[i][nome] = fn; } catch (e) {}
        }
    }

    var asstHasDragged = false;

    exp("asstAlterarQtd", function (codProd, delta) {
        var input = document.getElementById("asst-qty-" + codProd);
        if (!input) return;
        var q = parseInt(input.value, 10) || 1;
        q += delta;
        if (q < 1) q = 1;
        if (q > 9999) q = 9999;
        input.value = q;
    });

    // Alterar Preço Unitário e recalcular Margem Alvo (%) via fatorK da Unitrama
    exp("asstAlterarPreco", function (codProd) {
        var inputPreco = document.getElementById("asst-preco-" + codProd);
        var inputMargem = document.getElementById("asst-margem-" + codProd);
        if (!inputPreco || !inputMargem) return;

        var item = itensSugestoesMap[codProd];
        if (!item) return;

        var preco = parseNumeroBR(inputPreco.value);
        var K = Number(item.fatorK) || 0;

        if (preco > 0 && K > 0) {
            // m = (1 - (K / P)) * 100
            var margem = (1 - (K / preco)) * 100;
            inputMargem.value = formatarNumeroBR(margem, 2);
            if (margem < 0) {
                inputMargem.classList.add("asst-margem-negativa");
            } else {
                inputMargem.classList.remove("asst-margem-negativa");
            }
        }
    });

    // Alterar Margem Alvo (%) e recalcular Preço Unitário via fatorK da Unitrama
    exp("asstAlterarMargem", function (codProd) {
        var inputPreco = document.getElementById("asst-preco-" + codProd);
        var inputMargem = document.getElementById("asst-margem-" + codProd);
        if (!inputPreco || !inputMargem) return;

        var item = itensSugestoesMap[codProd];
        if (!item) return;

        var margem = parseNumeroBR(inputMargem.value);
        var K = Number(item.fatorK) || 0;

        if (margem < 0) {
            inputMargem.classList.add("asst-margem-negativa");
        } else {
            inputMargem.classList.remove("asst-margem-negativa");
        }

        if (margem < 100 && K > 0) {
            // P = K / (1 - (m / 100))
            var umMenosM = 1 - (margem / 100);
            if (umMenosM > 0) {
                var preco = K / umMenosM;
                inputPreco.value = formatarNumeroBR(preco, 2);
            }
        }
    });

    // Abrir Drawer: recolhe o balãozinho e abre o painel lateral deslizante
    exp("asstAbrirDrawer", function () {
        if (asstHasDragged) {
            asstHasDragged = false;
            return;
        }
        var drawers = document.querySelectorAll(".asst-drawer, #asst-drawer");
        for (var i = 0; i < drawers.length; i++) {
            drawers[i].classList.add("active");
        }
        var balloon = document.getElementById("asst-balloon");
        if (balloon) {
            balloon.style.display = "none";
        }
    });

    // Fechar Drawer / Minimizar para Balão: recolhe o drawer e reexibe o balãozinho
    exp("asstFecharDrawer", function () {
        var drawers = document.querySelectorAll(".asst-drawer, #asst-drawer");
        for (var i = 0; i < drawers.length; i++) {
            drawers[i].classList.remove("active");
        }
        var balloon = document.getElementById("asst-balloon");
        if (balloon) {
            balloon.style.display = "inline-flex";
            balloon.style.opacity = "1";
            balloon.style.transform = "scale(1)";
        }
        var help = document.getElementById("asst-help-box");
        if (help) {
            help.style.display = "none";
        }
    });

    // Botão Minimizar dedicado
    exp("asstMinimizarParaBalao", function () {
        if (typeof window.asstFecharDrawer === "function") {
            window.asstFecharDrawer();
        }
    });

    // Toggle pelo FAB redondo no canto inferior direito
    exp("asstToggleDrawer", function () {
        var drawer = document.getElementById("asst-drawer");
        var balloon = document.getElementById("asst-balloon");
        if (drawer) {
            var isActive = drawer.classList.contains("active");
            if (isActive) {
                drawer.classList.remove("active");
                if (balloon) {
                    balloon.style.display = "inline-flex";
                    balloon.style.opacity = "1";
                    balloon.style.transform = "scale(1)";
                }
                var help = document.getElementById("asst-help-box");
                if (help) help.style.display = "none";
            } else {
                drawer.classList.add("active");
                if (balloon) balloon.style.display = "none";
            }
        }
    });

    // Toggle da Caixa de Ajuda Explicativa da Unitrama
    exp("asstToggleAjuda", function () {
        var help = document.getElementById("asst-help-box");
        if (help) {
            help.style.display = (help.style.display === "none" || !help.style.display) ? "block" : "none";
        }
    });

    // Fechar Balãozinho Flutuante (mantém o FAB no canto)
    exp("asstFecharBaloon", function () {
        var balloon = document.getElementById("asst-balloon");
        if (balloon) {
            balloon.style.opacity = "0";
            balloon.style.transform = "scale(0.85)";
            setTimeout(function () {
                balloon.style.display = "none";
            }, 210);
        }
    });

    exp("asstMostrarToast", function (msg) {
        var toast = document.getElementById("asst-toast");
        var text = document.getElementById("asst-toast-text");
        if (toast) {
            if (text) text.textContent = msg;
            toast.classList.add("show");
            setTimeout(function () {
                toast.classList.remove("show");
            }, 3000);
        }
    });

    // Adicionar Item ao Pedido usando Operação Atômica Unificada no Backend
    exp("asstAdicionarAoPedido", function (codProd) {
        var btn = document.getElementById("asst-btn-" + codProd);
        var input = document.getElementById("asst-qty-" + codProd);
        var qtd = input ? (parseInt(input.value, 10) || 1) : 1;

        var inputPreco = document.getElementById("asst-preco-" + codProd);
        var item = itensSugestoesMap[codProd];
        var vlrFinal = inputPreco ? parseNumeroBR(inputPreco.value) : (item ? Number(item.vlrVenda) : 0);
        if (vlrFinal <= 0 && item && item.vlrVenda) {
            vlrFinal = Number(item.vlrVenda);
        }

        if (btn) {
            btn.disabled = true;
            btn.textContent = "Adicionando...";
        }

        var numActionID = Number(currentActionID) || 0;

        // Formatação nativa estrita conforme actionbutton.service.js e AbstractAction.java
        var execSource = {
            actionID: numActionID,
            masterEntityName: "CabecalhoNota",
            refreshType: "CURRENT",
            params: {
                param: [
                    { type: "S", paramName: "OPERACAO", $: "ADICIONAR_ITEM" },
                    { type: "I", paramName: "NUNOTA", $: String(nuNotaAtual) },
                    { type: "I", paramName: "CODPROD", $: String(codProd) },
                    { type: "F", paramName: "QTDNEG", $: String(qtd) },
                    { type: "F", paramName: "VLRUNIT", $: String(vlrFinal.toFixed(2)) }
                ]
            },
            rows: {
                row: [
                    {
                        master: "S",
                        entityName: "CabecalhoNota",
                        field: [
                            { fieldName: "NUNOTA", $: String(nuNotaAtual) }
                        ]
                    }
                ]
            }
        };

        var javaPayload = {
            javaCall: execSource
        };

        var callbackSucesso = function () {
            var fnToast = window.asstMostrarToast || (window.top && window.top.asstMostrarToast);
            if (fnToast) fnToast("Produto adicionado ao pedido!");

            var card = document.getElementById("asst-card-" + codProd);
            if (card) {
                card.classList.add("asst-card-removing");
                setTimeout(function () {
                    if (card && card.parentNode) {
                        card.parentNode.removeChild(card);
                    }
                }, 280);
            }

            itensSugestoes = itensSugestoes.filter(function (s) {
                return Number(s.codProd) !== Number(codProd);
            });

            var restantes = itensSugestoes.length;
            var elSub = document.getElementById("asst-balloon-subtitle");
            if (elSub) {
                if (restantes === 0) {
                    elSub.textContent = "Sem novas sugestões";
                    var emptyEl = document.getElementById("asst-drawer-empty");
                    if (emptyEl) emptyEl.style.display = "block";
                } else if (restantes === 1) {
                    elSub.textContent = "Ofereça este 1 item";
                } else {
                    elSub.textContent = "Ofereça estes " + restantes + " itens";
                }
            }

            // Atualização imediata e atômica da grade de itens na Central de Vendas
            recarregarItensETotaisCentral();
            setTimeout(recarregarItensETotaisCentral, 300);
            setTimeout(recarregarItensETotaisCentral, 700);
            ancorarPedidoAtual();
            setTimeout(ancorarPedidoAtual, 350);

            // Auto-refresh inteligente das sugestões após a inclusão do item no pedido
            setTimeout(function () {
                var fnAtualizar = window.asstAtualizarSugestoes || (window.top && window.top.asstAtualizarSugestoes);
                if (typeof fnAtualizar === "function") {
                    fnAtualizar(true);
                }
            }, 850);
        };

        var callbackErro = function (err) {
            console.warn("[AssistenteVendas] Erro ao adicionar produto:", err);
            if (btn) {
                btn.disabled = false;
                btn.textContent = "Adicionar";
            }
            var fnToast = window.asstMostrarToast || (window.top && window.top.asstMostrarToast);
            var msg = "Não foi possível adicionar o produto.";
            if (err) {
                if (err.statusMessage) msg = err.statusMessage;
                else if (err.message) msg = err.message;
                else if (typeof err === "string") msg = err;
            }
            if (fnToast) fnToast(msg);
        };

        var sp = (typeof ServiceProxy !== "undefined" && ServiceProxy) 
            ? ServiceProxy 
            : (window.ServiceProxy || (window.parent && window.parent.ServiceProxy) || (window.top && window.top.ServiceProxy));

        if (sp && sp.callService) {
            sp.callService("ActionButtonsSP.executeJava", javaPayload).then(
                function (resp) {
                    if (resp && resp.status === "0") {
                        callbackErro(resp);
                    } else {
                        callbackSucesso();
                    }
                },
                function (err) {
                    callbackErro(err);
                }
            );
        } else {
            fetch("/mge/service.sbr?serviceName=ActionButtonsSP.executeJava&outputType=json", {
                method: "POST",
                headers: { "Content-Type": "application/json;charset=UTF-8" },
                body: JSON.stringify({
                    serviceName: "ActionButtonsSP.executeJava",
                    requestBody: javaPayload
                })
            }).then(function (res) {
                return res.json();
            }).then(function (data) {
                if (data && (data.status === "1" || data.status === "2" || data.responseBody)) {
                    callbackSucesso();
                } else {
                    var errMsg = (data && data.statusMessage) ? data.statusMessage : "Erro ao adicionar produto.";
                    callbackErro({ statusMessage: errMsg });
                }
            }).catch(function (fetchErr) {
                callbackErro(fetchErr);
            });
        }
    });

    // Atualizar sugestões dinamicamente (Manual via botão ou Automático após inclusão)
    exp("asstAtualizarSugestoes", function (silencioso) {
        var btnRefresh = document.getElementById("asst-btn-refresh-sugestoes");
        if (btnRefresh) {
            btnRefresh.classList.add("asst-spin");
        }

        var fnToast = window.asstMostrarToast || (window.top && window.top.asstMostrarToast);
        if (!silencioso && fnToast) {
            fnToast("Buscando novas sugestões...");
        }

        // Tenta obter o NUNOTA atualizado caso ainda esteja 0
        if (!nuNotaAtual || nuNotaAtual <= 0) {
            try {
                var list = encontrarDatasetsCabecalho();
                for (var i = 0; i < list.length; i++) {
                    var row = list[i].getCurrentRow ? list[i].getCurrentRow() : (list[i].getSelectedRow ? list[i].getSelectedRow() : null);
                    if (row) {
                        var vNota = row.NUNOTA || (row.get && row.get("NUNOTA"));
                        if (vNota) {
                            nuNotaAtual = Number(vNota);
                            break;
                        }
                    }
                }
            } catch (eNota) {}
        }

        if (!nuNotaAtual || nuNotaAtual <= 0) {
            if (btnRefresh) btnRefresh.classList.remove("asst-spin");
            if (!silencioso && fnToast) fnToast("Nenhum pedido selecionado.");
            return;
        }

        var numActionID = Number(currentActionID) || 0;

        var execSource = {
            actionID: numActionID,
            masterEntityName: "CabecalhoNota",
            refreshType: "NONE",
            params: {
                param: [
                    { type: "S", paramName: "OPERACAO", $: "OBTER_SUGESTOES" },
                    { type: "I", paramName: "NUNOTA", $: String(nuNotaAtual) }
                ]
            },
            rows: {
                row: [
                    {
                        master: "S",
                        entityName: "CabecalhoNota",
                        field: [
                            { fieldName: "NUNOTA", $: String(nuNotaAtual) }
                        ]
                    }
                ]
            }
        };

        var javaPayload = {
            javaCall: execSource
        };

        var finalizarLoading = function () {
            if (btnRefresh) {
                setTimeout(function () {
                    btnRefresh.classList.remove("asst-spin");
                }, 400);
            }
        };

        var url = "/mge/service.sbr?serviceName=ActionButtonsSP.executeJava&outputType=json";
        if (location.search && location.search.indexOf("mgeSession=") > -1) {
            var match = location.search.match(/mgeSession=([^&]+)/);
            if (match && match[1]) {
                url += "&mgeSession=" + match[1];
            }
        }

        fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json;charset=UTF-8" },
            body: JSON.stringify({
                serviceName: "ActionButtonsSP.executeJava",
                requestBody: javaPayload
            })
        }).then(function (res) {
            return res.json();
        }).then(function (data) {
            finalizarLoading();
            if (data && data.statusMessage) {
                try {
                    var parsed = JSON.parse(data.statusMessage);
                    if (Array.isArray(parsed)) {
                        itensSugestoes = parsed;
                        indexarSugestoesMap();
                        renderizarDados();
                        if (!silencioso && fnToast) {
                            if (itensSugestoes.length > 0) {
                                fnToast(itensSugestoes.length + " sugestão(ões) atualizada(s)!");
                            } else {
                                fnToast("Sem novas sugestões para este pedido.");
                            }
                        }
                        return;
                    }
                } catch (eParse) {
                    console.warn("[AssistenteVendas] Resposta de sugestões não é JSON:", data.statusMessage);
                }
            }

            if (!silencioso && fnToast) {
                fnToast("Sugestões atualizadas.");
            }
        }).catch(function (err) {
            finalizarLoading();
            console.warn("[AssistenteVendas] Erro ao buscar novas sugestões:", err);
            if (!silencioso && fnToast) {
                fnToast("Não foi possível atualizar as sugestões.");
            }
        });
    });

    function recarregarItensETotaisCentral() {
        console.log("[AssistenteVendas] Recarregando grade de itens e totais do pedido #" + nuNotaAtual + "...");
        var winList = coletarTodasJanelas();
        var recarregouItens = false;

        for (var w = 0; w < winList.length; w++) {
            var win = winList[w];
            if (!win || !win.document) continue;
            var d = win.document;
            var ng = win.angular || (window.top && window.top.angular) || window.angular;

            // 1. Método Físico Primário: Clique no botão refresh nativo de itens (#IdNavgator .btn-refresh)
            try {
                var btnRefresh = d.querySelector("#IdNavgator .btn-refresh, sk-navigator[dataset*='dsItemNota'] .btn-refresh, [dataset='dsItemNota'] .btn-refresh, #IdNavgator button.btn-refresh");
                if (btnRefresh) {
                    console.log("[AssistenteVendas] Disparando clique no botão físico de refresh dos itens");
                    btnRefresh.click();
                    recarregouItens = true;
                }
            } catch (eBtn) {
                console.warn("[AssistenteVendas] Aviso ao clicar no botão refresh dos itens:", eBtn);
            }

            // 2. Método Secundário via Controller de Itens ou Scope
            if (ng) {
                try {
                    var gradeItensEls = d.querySelectorAll("sk-grade-itens, [ng-controller*='GradeItensController'], #IdNavgator, [sk-dataset='dsItemNota'], [dataset='dsItemNota'], sk-datagrid[sk-dataset='dsItemNota'], sk-double-face-panel[sk-dataset='dsItemNota']");
                    for (var g = 0; g < gradeItensEls.length; g++) {
                        var gEl = gradeItensEls[g];
                        var gNg = ng.element(gEl);
                        var gCtrl = gNg.controller("skGradeItens") || gNg.controller();
                        if (gCtrl) {
                            if (typeof gCtrl.getDataSet === "function") {
                                var dsItensG = gCtrl.getDataSet();
                                if (dsItensG && typeof dsItensG.refresh === "function") {
                                    dsItensG.refresh();
                                    recarregouItens = true;
                                }
                            }
                        }
                        var gScope = gNg.scope();
                        if (gScope) {
                            if (gScope.dsItemNota && typeof gScope.dsItemNota.refresh === "function") {
                                gScope.dsItemNota.refresh();
                                recarregouItens = true;
                            } else if (gScope.dataset && typeof gScope.dataset.refresh === "function") {
                                gScope.dataset.refresh();
                                recarregouItens = true;
                            } else if (typeof gScope.refresh === "function") {
                                gScope.refresh();
                                recarregouItens = true;
                            }
                        }
                    }
                } catch (eGrade) {}

                // 3. Atualizar totais e impostos do cabeçalho da nota (TGFCAB) com delay suave para evitar concorrência
                try {
                    var centralEls = d.querySelectorAll("sk-central-notas-html5, [ng-controller*='CentralNotasController'], #datasetMaster");
                    for (var c = 0; c < centralEls.length; c++) {
                        var cEl = ng.element(centralEls[c]);
                        var cCtrl = cEl.controller("skCentralNotasHtml5") || cEl.controller();
                        var dsCab = (cCtrl && typeof cCtrl.getDataSet === "function") ? cCtrl.getDataSet() : (cEl.scope() ? cEl.scope().datasetMaster : null);
                        if (dsCab && typeof dsCab.refreshCurrentRow === "function") {
                            setTimeout(function () {
                                try { dsCab.refreshCurrentRow(); } catch (e) {}
                            }, 250);
                        }
                    }
                } catch (eCentral) {}
            }
        }
    }

    var recarregarGradeCentral = recarregarItensETotaisCentral;

    // 6. Configurar Arrasto do Balãozinho
    function configurarArrasto() {
        var balloon = document.getElementById("asst-balloon");
        var handle = document.getElementById("asst-drag-handle");
        if (!balloon) return;

        var isDragging = false;
        var startX = 0, startY = 0, initLeft = 0, initTop = 0;

        var onStart = function (clientX, clientY) {
            isDragging = true;
            asstHasDragged = false;
            startX = clientX;
            startY = clientY;

            var rect = balloon.getBoundingClientRect();
            initLeft = rect.left;
            initTop = rect.top;
            balloon.classList.add("asst-dragging");
        };

        var onMove = function (clientX, clientY) {
            if (!isDragging) return;
            var dx = clientX - startX;
            var dy = clientY - startY;

            if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
                asstHasDragged = true;
            }

            var nLeft = initLeft + dx;
            var nTop = initTop + dy;

            var maxL = window.innerWidth - balloon.offsetWidth - 10;
            var maxT = window.innerHeight - balloon.offsetHeight - 10;

            if (nLeft < 10) nLeft = 10;
            if (nTop < 10) nTop = 10;
            if (nLeft > maxL) nLeft = maxL;
            if (nTop > maxT) nTop = maxT;

            balloon.style.left = nLeft + "px";
            balloon.style.top = nTop + "px";
            balloon.style.right = "auto";
            balloon.style.bottom = "auto";
        };

        var onEnd = function () {
            if (isDragging) {
                isDragging = false;
                balloon.classList.remove("asst-dragging");
            }
        };

        if (handle) {
            handle.addEventListener("mousedown", function (e) {
                onStart(e.clientX, e.clientY);
                e.preventDefault();
            });
        }
        balloon.addEventListener("mousedown", function (e) {
            if (e.target.closest && e.target.closest("button")) return;
            onStart(e.clientX, e.clientY);
        });

        document.addEventListener("mousemove", function (e) {
            onMove(e.clientX, e.clientY);
        });

        document.addEventListener("mouseup", onEnd);

        if (handle) {
            handle.addEventListener("touchstart", function (e) {
                if (e.touches && e.touches[0]) {
                    onStart(e.touches[0].clientX, e.touches[0].clientY);
                }
            }, { passive: true });
        }
        document.addEventListener("touchmove", function (e) {
            if (e.touches && e.touches[0]) {
                onMove(e.touches[0].clientX, e.touches[0].clientY);
            }
        }, { passive: true });
        document.addEventListener("touchend", onEnd);
    }

    function neutralizarHostModal() {
        try {
            var asstRoot = document.getElementById("assistente-root");
            var myP = document.getElementById("myPopUp");
            var el = asstRoot || myP;
            if (!el) return;

            var mH = el.closest ? el.closest(".modal") : null;
            if (!mH) {
                var p = el.parentElement;
                while (p && (!p.classList || !p.classList.contains("modal"))) {
                    p = p.parentElement;
                }
                mH = p;
            }

            if (mH) {
                mH.classList.add("asst-modal-host");
                mH.style.setProperty("pointer-events", "none", "important");
                mH.style.setProperty("background", "transparent", "important");
                mH.style.setProperty("border", "none", "important");
                mH.style.setProperty("box-shadow", "none", "important");

                var dlg = mH.querySelector(".modal-dialog");
                if (dlg) {
                    dlg.style.setProperty("position", "fixed", "important");
                    dlg.style.setProperty("top", "0", "important");
                    dlg.style.setProperty("left", "0", "important");
                    dlg.style.setProperty("width", "100vw", "important");
                    dlg.style.setProperty("height", "100vh", "important");
                    dlg.style.setProperty("max-width", "100vw", "important");
                    dlg.style.setProperty("max-height", "100vh", "important");
                    dlg.style.setProperty("margin", "0", "important");
                    dlg.style.setProperty("padding", "0", "important");
                    dlg.style.setProperty("background", "transparent", "important");
                    dlg.style.setProperty("border", "none", "important");
                    dlg.style.setProperty("box-shadow", "none", "important");
                    dlg.style.setProperty("pointer-events", "none", "important");
                    dlg.style.setProperty("transform", "none", "important");
                    dlg.style.setProperty("-webkit-transform", "none", "important");
                }

                var cnt = mH.querySelector(".modal-content");
                if (cnt) {
                    cnt.style.setProperty("position", "fixed", "important");
                    cnt.style.setProperty("top", "0", "important");
                    cnt.style.setProperty("left", "0", "important");
                    cnt.style.setProperty("width", "100vw", "important");
                    cnt.style.setProperty("height", "100vh", "important");
                    cnt.style.setProperty("background", "transparent", "important");
                    cnt.style.setProperty("border", "none", "important");
                    cnt.style.setProperty("box-shadow", "none", "important");
                    cnt.style.setProperty("border-radius", "0", "important");
                    cnt.style.setProperty("pointer-events", "none", "important");
                }

                var hdr = mH.querySelector(".modal-header");
                if (hdr) hdr.style.setProperty("display", "none", "important");

                var ftr = mH.querySelector(".modal-footer");
                if (ftr) ftr.style.setProperty("display", "none", "important");

                var bdy = mH.querySelector(".modal-body");
                if (bdy) {
                    bdy.style.setProperty("position", "fixed", "important");
                    bdy.style.setProperty("top", "0", "important");
                    bdy.style.setProperty("left", "0", "important");
                    bdy.style.setProperty("width", "100vw", "important");
                    bdy.style.setProperty("height", "100vh", "important");
                    bdy.style.setProperty("margin", "0", "important");
                    bdy.style.setProperty("padding", "0", "important");
                    bdy.style.setProperty("background", "transparent", "important");
                    bdy.style.setProperty("border", "none", "important");
                    bdy.style.setProperty("pointer-events", "none", "important");
                    bdy.style.setProperty("overflow", "visible", "important");
                }

                var prev = mH.previousElementSibling;
                if (prev && prev.classList && prev.classList.contains("modal-backdrop")) {
                    prev.classList.add("asst-modal-backdrop-hidden");
                    prev.style.setProperty("display", "none", "important");
                    prev.style.setProperty("opacity", "0", "important");
                    prev.style.setProperty("pointer-events", "none", "important");
                    prev.style.setProperty("width", "0", "important");
                    prev.style.setProperty("height", "0", "important");
                } else if (mH.parentNode) {
                    var backdrops = mH.parentNode.querySelectorAll(".modal-backdrop");
                    for (var b = 0; b < backdrops.length; b++) {
                        backdrops[b].classList.add("asst-modal-backdrop-hidden");
                        backdrops[b].style.setProperty("display", "none", "important");
                        backdrops[b].style.setProperty("opacity", "0", "important");
                        backdrops[b].style.setProperty("pointer-events", "none", "important");
                        backdrops[b].style.setProperty("width", "0", "important");
                        backdrops[b].style.setProperty("height", "0", "important");
                    }
                }
            }
        } catch (e) {
            console.warn("[AssistenteVendas] Aviso ao neutralizar host modal:", e);
        }
    }

    // 7. Inicialização do Ciclo de Vida
    // OBS: O drawer permanece FECHADO inicialmente. Exibe-se APENAS o balãozinho e o FAB flutuantes!
    function inicializar() {
        neutralizarRefreshAcao();
        protegerRecordsReloader();
        ancorarPedidoAtual();

        neutralizarHostModal();
        setTimeout(neutralizarHostModal, 20);
        setTimeout(neutralizarHostModal, 80);
        setTimeout(neutralizarHostModal, 200);

        renderizarDados();
        configurarArrasto();

        // Garantir que o drawer começa recolhido (fora da tela à direita)
        var drawers = document.querySelectorAll(".asst-drawer, #asst-drawer");
        for (var i = 0; i < drawers.length; i++) {
            drawers[i].classList.remove("active");
        }

        // Garantir que o balãozinho esteja visível
        var balloon = document.getElementById("asst-balloon");
        if (balloon) {
            balloon.style.display = "inline-flex";
            balloon.style.opacity = "1";
            balloon.style.transform = "scale(1)";
        }

        // Reforço temporizado contra callbacks assíncronos do ActionButtonService
        setTimeout(function () {
            neutralizarRefreshAcao();
            protegerRecordsReloader();
            ancorarPedidoAtual();
        }, 50);

        setTimeout(function () {
            neutralizarRefreshAcao();
            protegerRecordsReloader();
            ancorarPedidoAtual();
        }, 150);

        setTimeout(function () {
            ancorarPedidoAtual();
        }, 350);

        setTimeout(function () {
            ancorarPedidoAtual();
        }, 700);

        setTimeout(function () {
            ancorarPedidoAtual();
        }, 1500);

        console.log("[AssistenteVendas Unitrama] Ativado com sucesso: balãozinho visível na Central de Vendas!");
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", inicializar);
    } else {
        setTimeout(inicializar, 60);
    }
})();
