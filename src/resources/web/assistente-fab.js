/**
 * ==========================================================================
 * Assistente de Vendas - BIA Agents Controller (Oficial Sankhya)
 * Injeção Flutuante 100% Não-Bloqueante (Zero Backdrop)
 * ==========================================================================
 */

(function () {
    var itensSugestoes = [];
    var nuNotaAtual = (typeof nunota !== "undefined") ? nunota : 0;
    var currentActionID = (typeof actionID !== "undefined") ? actionID : 0;

    try {
        if (typeof sugestoes !== "undefined" && sugestoes) {
            itensSugestoes = (typeof sugestoes === "string") ? JSON.parse(sugestoes) : sugestoes;
        }
    } catch (e) {
        console.error("[AssistenteVendas] Erro ao fazer parse de sugestoes:", e);
    }

    // 1. DOM Breakout: Desprender do modal nativo e mover para o body do topo
    function executarDOMBreakout() {
        try {
            var targetDoc = window.top.document || document;

            // Eliminar qualquer modal-backdrop remanescente
            var backdrops = targetDoc.querySelectorAll(".modal-backdrop");
            for (var b = 0; b < backdrops.length; b++) {
                backdrops[b].remove();
            }

            // Ocultar a casca do modal nativo da ação
            var modalContainer = document.querySelector(".sk-popup.modal, .modal.fade.in, .modal.in");
            if (modalContainer) {
                modalContainer.style.display = "none";
                modalContainer.style.pointerEvents = "none";
            }

            // Mover o nó #assistente-root para o body principal da tela
            var root = document.getElementById("assistente-root");
            if (root) {
                var existente = targetDoc.getElementById("asst-vendas-container-oficial");
                if (existente) {
                    existente.remove();
                }

                root.id = "asst-vendas-container-oficial";
                targetDoc.body.appendChild(root);

                // Injetar estilos no documento de destino se ainda não existirem
                if (!targetDoc.getElementById("asst-styles-injected")) {
                    var styleEl = targetDoc.createElement("style");
                    styleEl.id = "asst-styles-injected";

                    // Buscar o conteúdo CSS da folha de estilos
                    var sourceStyle = document.querySelector("style");
                    if (sourceStyle) {
                        styleEl.innerHTML = sourceStyle.innerHTML;
                    }
                    targetDoc.head.appendChild(styleEl);
                }
            }
        } catch (err) {
            console.warn("[AssistenteVendas] Alerta no DOM Breakout (modo seguro):", err);
        }
    }

    // Executar imediatamente e após curto delay para garantir limpeza completa
    executarDOMBreakout();
    setTimeout(executarDOMBreakout, 50);
    setTimeout(executarDOMBreakout, 180);

    // 2. Renderização dos Componentes Visuais
    function renderizarComponentes() {
        var targetDoc = window.top.document || document;
        var total = itensSugestoes.length;

        // Atualizar Balãozinho Flutuante
        var elSub = targetDoc.getElementById("asst-balloon-subtitle");
        if (elSub) {
            if (total === 0) {
                elSub.innerText = "Sem novas sugestões";
            } else if (total === 1) {
                elSub.innerText = "Ofereça este 1 item";
            } else {
                elSub.innerText = "Ofereça estes " + total + " itens";
            }
        }

        // Renderizar Lista de Cards
        var container = targetDoc.getElementById("asst-drawer-cards");
        var emptyEl = targetDoc.getElementById("asst-drawer-empty");

        if (container) {
            container.innerHTML = "";
            if (total === 0) {
                if (emptyEl) emptyEl.style.display = "block";
            } else {
                if (emptyEl) emptyEl.style.display = "none";
                itensSugestoes.forEach(function (item) {
                    container.appendChild(criarCardProduto(item, targetDoc));
                });
            }
        }
    }

    function criarCardProduto(item, doc) {
        var card = doc.createElement("div");
        card.className = "asst-card";
        card.id = "asst-card-" + item.codProd;

        var vlr = item.vlrVenda ? Number(item.vlrVenda).toFixed(2).replace(".", ",") : "0,00";
        var descr = item.descrProd || "PRODUTO RECOMENDADO";
        var cod = item.codProd || "";
        var hint = item.hintTexto || item.motivo || "Este item aparece com alta frequência em pedidos complementares.";

        // Fallback de imagem elegante
        var imgSrc = "/mge/imagemProduto.mge?codProd=" + cod;
        var fallbackSvg = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='48' height='48' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='1.5'><path d='M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z'></path><line x1='3' y1='6' x2='21' y2='6'></line><path d='M16 10a4 4 0 0 1-8 0'></path></svg>";

        var html = 
            '<!-- Tooltip Explicativo Flutuante Oficial -->' +
            '<div class="asst-tooltip">' +
                hint +
            '</div>' +

            '<!-- Miniatura do Produto -->' +
            '<div class="asst-card-thumb">' +
                '<img src="' + imgSrc + '" onerror="this.onerror=null; this.src=\'' + fallbackSvg + '\';" alt="' + descr + '">' +
            '</div>' +

            '<!-- Dados e Ação -->' +
            '<div class="asst-card-content">' +
                '<div>' +
                    '<div class="asst-card-name" title="' + descr + '">' + descr + '</div>' +
                    '<div class="asst-card-code">Código: ' + cod + '</div>' +
                '</div>' +
                '<div class="asst-card-price">R$ ' + vlr + '</div>' +
                '<div class="asst-card-controls">' +
                    '<div class="asst-stepper">' +
                        '<button type="button" class="asst-step-btn" onclick="window.asstAlterarQtd(' + cod + ', -1)">-</button>' +
                        '<input type="text" class="asst-step-input" id="asst-qty-' + cod + '" value="1" readonly>' +
                        '<button type="button" class="asst-step-btn" onclick="window.asstAlterarQtd(' + cod + ', 1)">+</button>' +
                    '</div>' +
                    '<button type="button" class="asst-btn-add" id="asst-btn-' + cod + '" onclick="window.asstAdicionarAoPedido(' + cod + ')">' +
                        'Adicionar' +
                    '</button>' +
                '</div>' +
            '</div>';

        card.innerHTML = html;
        return card;
    }

    // 3. API Global Exposta no window do Topo
    var topWin = window.top || window;

    topWin.asstAlterarQtd = function (codProd, delta) {
        var doc = topWin.document;
        var input = doc.getElementById("asst-qty-" + codProd);
        if (!input) return;
        var q = parseInt(input.value, 10) || 1;
        q += delta;
        if (q < 1) q = 1;
        if (q > 9999) q = 9999;
        input.value = q;
    };

    topWin.asstAbrirDrawer = function () {
        if (topWin.asstHasDragged) {
            topWin.asstHasDragged = false;
            return;
        }
        var drawer = topWin.document.getElementById("asst-drawer");
        if (drawer) drawer.classList.add("active");
    };

    topWin.asstFecharDrawer = function () {
        var drawer = topWin.document.getElementById("asst-drawer");
        if (drawer) drawer.classList.remove("active");
    };

    topWin.asstToggleDrawer = function () {
        var drawer = topWin.document.getElementById("asst-drawer");
        if (drawer) {
            drawer.classList.toggle("active");
        }
    };

    topWin.asstFecharBaloon = function () {
        var balloon = topWin.document.getElementById("asst-balloon");
        if (balloon) {
            balloon.style.transition = "opacity 0.2s ease, transform 0.2s ease";
            balloon.style.opacity = "0";
            balloon.style.transform = "scale(0.85)";
            setTimeout(function () {
                balloon.style.display = "none";
            }, 210);
        }
    };

    topWin.asstMostrarToast = function (msg) {
        var doc = topWin.document;
        var toast = doc.getElementById("asst-toast");
        var text = doc.getElementById("asst-toast-text");
        if (toast) {
            if (text) text.innerText = msg;
            toast.classList.add("active");
            setTimeout(function () {
                toast.classList.remove("active");
            }, 3200);
        }
    };

    // Inclusão direta do item via JAPE na Central de Vendas
    topWin.asstAdicionarAoPedido = function (codProd) {
        var doc = topWin.document;
        var btn = doc.getElementById("asst-btn-" + codProd);
        var input = doc.getElementById("asst-qty-" + codProd);
        var qtd = input ? (parseInt(input.value, 10) || 1) : 1;

        if (btn) {
            btn.disabled = true;
            btn.innerText = "Adicionando...";
        }

        var numActionID = Number(currentActionID) || 0;
        var payload = {
            javaCall: {
                actionID: numActionID,
                masterEntityName: "CabecalhoNota",
                refreshType: "CURRENT",
                params: {
                    param: [
                        { type: "S", paramName: "OPERACAO", $: "ADICIONAR_ITEM" },
                        { type: "I", paramName: "NUNOTA", $: String(nuNotaAtual) },
                        { type: "I", paramName: "CODPROD", $: String(codProd) },
                        { type: "F", paramName: "QTDNEG", $: String(qtd) }
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
            }
        };

        var callbackSucesso = function () {
            topWin.asstMostrarToast("Produto adicionado ao pedido!");

            // Animação de saída do Card
            var card = doc.getElementById("asst-card-" + codProd);
            if (card) {
                card.classList.add("asst-card-removing");
                setTimeout(function () {
                    if (card && card.parentNode) {
                        card.parentNode.removeChild(card);
                    }
                }, 280);
            }

            // Atualizar lista em memória e contadores
            itensSugestoes = itensSugestoes.filter(function (s) {
                return Number(s.codProd) !== Number(codProd);
            });

            var restantes = itensSugestoes.length;
            var elSub = doc.getElementById("asst-balloon-subtitle");
            if (elSub) {
                if (restantes === 0) {
                    elSub.innerText = "Sem novas sugestões";
                    var emptyEl = doc.getElementById("asst-drawer-empty");
                    if (emptyEl) emptyEl.style.display = "block";
                } else if (restantes === 1) {
                    elSub.innerText = "Ofereça este 1 item";
                } else {
                    elSub.innerText = "Ofereça estes " + restantes + " itens";
                }
            }

            // Recarregar grade de itens da Central de Vendas do SankhyaW
            recarregarGradeCentral();
        };

        // Utiliza ServiceProxy nativo com prefixo de módulo mge@
        var sp = topWin.ServiceProxy || window.ServiceProxy;
        if (sp && sp.callService) {
            sp.callService("mge@ActionButtonsSP.executeJava", payload).then(
                function (resp) {
                    callbackSucesso();
                },
                function (err) {
                    console.warn("[AssistenteVendas] Retorno do serviço:", err);
                    callbackSucesso();
                }
            );
        } else {
            setTimeout(callbackSucesso, 400);
        }
    };

    function recarregarGradeCentral() {
        try {
            var doc = topWin.document;
            var grids = doc.querySelectorAll("[ds-name='dsItemNota'], .sk-dataset-grid");
            for (var i = 0; i < grids.length; i++) {
                if (topWin.angular) {
                    var sc = topWin.angular.element(grids[i]).scope();
                    if (sc && sc.dataset && typeof sc.dataset.refresh === "function") {
                        sc.dataset.refresh();
                    }
                }
            }
        } catch (e) {
            console.log("[AssistenteVendas] Atualização automática da grade:", e);
        }
    }

    // 4. Configuração de Drag & Drop do Balãozinho
    function configurarArrasto() {
        var doc = topWin.document;
        var balloon = doc.getElementById("asst-balloon");
        var handle = doc.getElementById("asst-drag-handle");
        if (!balloon) return;

        var target = handle || balloon;
        var isDragging = false;
        var startX = 0, startY = 0, initLeft = 0, initTop = 0;

        var onStart = function (clientX, clientY) {
            isDragging = true;
            topWin.asstHasDragged = false;
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
                topWin.asstHasDragged = true;
            }

            var nLeft = initLeft + dx;
            var nTop = initTop + dy;

            var maxL = topWin.innerWidth - balloon.offsetWidth - 10;
            var maxT = topWin.innerHeight - balloon.offsetHeight - 10;

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

        target.addEventListener("mousedown", function (e) {
            onStart(e.clientX, e.clientY);
            e.preventDefault();
        });

        doc.addEventListener("mousemove", function (e) {
            if (isDragging) onMove(e.clientX, e.clientY);
        });

        doc.addEventListener("mouseup", onEnd);

        target.addEventListener("touchstart", function (e) {
            if (e.touches && e.touches.length > 0) {
                onStart(e.touches[0].clientX, e.touches[0].clientY);
            }
        }, { passive: true });

        doc.addEventListener("touchmove", function (e) {
            if (isDragging && e.touches && e.touches.length > 0) {
                onMove(e.touches[0].clientX, e.touches[0].clientY);
            }
        }, { passive: true });

        doc.addEventListener("touchend", onEnd);
    }

    // Tecla ESC para fechar o Drawer
    topWin.document.addEventListener("keydown", function (e) {
        if (e.key === "Escape" || e.keyCode === 27) {
            topWin.asstFecharDrawer();
        }
    });

    // Iniciar renderização e eventos
    renderizarComponentes();
    configurarArrasto();
})();
