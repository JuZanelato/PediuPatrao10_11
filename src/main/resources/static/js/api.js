(function () {
    function meta(nome) {
        const el = document.querySelector('meta[name="' + nome + '"]');
        return el ? el.getAttribute('content') : null;
    }

    window.apiFetch = async function (url, opcoes = {}) {
        const metodo = (opcoes.method || 'GET').toUpperCase();
        const headers = Object.assign({ 'Accept': 'application/json' }, opcoes.headers || {});
        if (opcoes.body !== undefined && typeof opcoes.body !== 'string') {
            headers['Content-Type'] = 'application/json';
            opcoes.body = JSON.stringify(opcoes.body);
        }
        if (metodo !== 'GET') {
            const token = meta('_csrf');
            const header = meta('_csrf_header');
            if (token && header) headers[header] = token;
        }
        const resp = await fetch(url, Object.assign({}, opcoes, { method: metodo, headers }));
        let dados = null;
        const texto = await resp.text();
        if (texto) {
            try { dados = JSON.parse(texto); } catch (e) { dados = texto; }
        }
        if (!resp.ok) {
            let msg = (dados && dados.mensagem) ? dados.mensagem : ('Erro HTTP ' + resp.status);
            if (resp.status === 401) msg = 'Sessão expirada. Faça login novamente.';
            if (resp.status === 403 && !(dados && dados.mensagem)) msg = 'Acesso negado para o seu perfil.';
            const erro = new Error(msg);
            erro.status = resp.status;
            throw erro;
        }
        return dados;
    };

    window.esc = function (valor) {
        if (valor === null || valor === undefined) return '';
        return String(valor)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };

    window.formatarMoeda = function (v) {
        return Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    };

    window.formatarDataHora = function (v) {
        if (!v) return '—';
        const d = new Date(v);
        return isNaN(d) ? v : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
    };
})();
