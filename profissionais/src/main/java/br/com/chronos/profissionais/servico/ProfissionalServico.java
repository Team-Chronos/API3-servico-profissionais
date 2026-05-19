package br.com.chronos.profissionais.servico;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.chronos.profissionais.api.dto.ProfissionalRequisicao;
import br.com.chronos.profissionais.api.dto.ProfissionalResposta;
import br.com.chronos.profissionais.api.dto.ProjetoVinculoRequisicao;
import br.com.chronos.profissionais.dominio.Profissional;
import br.com.chronos.profissionais.dominio.ProfissionalProjeto;
import br.com.chronos.profissionais.dominio.ProfissionalProjetoId;
import br.com.chronos.profissionais.dominio.Projeto;
import br.com.chronos.profissionais.repositorio.ProfissionalProjetoRepositorio;
import br.com.chronos.profissionais.repositorio.ProfissionalRepositorio;
import br.com.chronos.profissionais.repositorio.ProjetoRepositorio;
import jakarta.persistence.EntityNotFoundException;

@Service
public class ProfissionalServico {

    private final ProfissionalRepositorio profissionalRepositorio;
    private final ProjetoRepositorio projetoRepositorio;
    private final ProfissionalProjetoRepositorio profissionalProjetoRepositorio;

    public ProfissionalServico(ProfissionalRepositorio profissionalRepositorio,
            ProjetoRepositorio projetoRepositorio,
            ProfissionalProjetoRepositorio profissionalProjetoRepositorio) {
        this.profissionalRepositorio = profissionalRepositorio;
        this.projetoRepositorio = projetoRepositorio;
        this.profissionalProjetoRepositorio = profissionalProjetoRepositorio;
    }

    @Transactional
    @CacheEvict(value = { "profissionais", "profissional", "projetos-disponiveis",
            "projetos-vinculados" }, allEntries = true)
    public ProfissionalResposta criar(ProfissionalRequisicao request) {
        if (profissionalRepositorio.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Ja existe profissional com esse email.");
        }
        Profissional profissional = new Profissional();
        preencherDados(profissional, request);
        Profissional salvo = profissionalRepositorio.save(profissional);
        sincronizarVinculos(salvo, request.projetos());
        return buscarResposta(salvo.getId());
    }

    @Transactional
    @CacheEvict(value = { "profissionais", "profissional", "projetos-disponiveis",
            "projetos-vinculados" }, allEntries = true)
    public ProfissionalResposta atualizar(int id, ProfissionalRequisicao request) {
        Profissional profissional = buscarProfissionalOuFalhar(id);
        if (!profissional.getEmail().equalsIgnoreCase(request.email())
                && profissionalRepositorio.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Ja existe profissional com esse email.");
        }
        preencherDados(profissional, request);
        Profissional salvo = profissionalRepositorio.save(profissional);
        sincronizarVinculos(salvo, request.projetos());
        return buscarResposta(salvo.getId());
    }

    @Transactional
    @CacheEvict(value = { "profissionais", "profissional", "projetos-vinculados" }, allEntries = true)
    public void deletar(int id) {
        Profissional profissional = buscarProfissionalOuFalhar(id);
        if (profissionalProjetoRepositorio.existeVinculoPorProfissional(id)) {
            throw new IllegalArgumentException("Não é possivel excluir o profissional pois ele esta vinculado a projetos. Remova os vinculos antes de excluir.");
        }
        profissionalRepositorio.delete(profissional);
    }

    @Transactional
    @CacheEvict(value = { "profissional", "projetos-vinculados" }, allEntries = true)
    public void vincularProjeto(int profissionalId, int projetoId, double valorHora) {
        Profissional profissional = buscarProfissionalOuFalhar(profissionalId);
        Projeto projeto = projetoRepositorio.findById(projetoId)
                .orElseThrow(() -> new EntityNotFoundException("Projeto nao encontrado."));
        ProfissionalProjetoId id = new ProfissionalProjetoId(projeto.getId(), profissional.getId());
        ProfissionalProjeto vinculo = profissionalProjetoRepositorio.findById(id).orElseGet(ProfissionalProjeto::new);
        vinculo.setId(id);
        vinculo.setProfissional(profissional);
        vinculo.setProjeto(projeto);
        vinculo.setValorHora(BigDecimal.valueOf(valorHora));
        profissionalProjetoRepositorio.save(vinculo);
    }

    @Transactional
    @CacheEvict(value = { "profissional", "projetos-vinculados" }, allEntries = true)
    public void desvincularProjeto(int profissionalId, int projetoId) {
        ProfissionalProjetoId id = new ProfissionalProjetoId(projetoId, profissionalId);
        if (!profissionalProjetoRepositorio.existsById(id)) {
            throw new EntityNotFoundException("Vinculo nao encontrado.");
        }
        profissionalProjetoRepositorio.deleteById(id);
    }

    private ProfissionalResposta buscarResposta(int id) {
        Profissional profissional = buscarProfissionalOuFalhar(id);
        return new ProfissionalResposta(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getAtivo(),
                profissional.getCargoId(),
                List.of());
    }

    private Profissional buscarProfissionalOuFalhar(int id) {
        return profissionalRepositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional nao encontrado."));
    }

    private void preencherDados(Profissional profissional, ProfissionalRequisicao request) {
        profissional.setNome(request.nome());
        profissional.setEmail(request.email());
        if (request.senhaHash() != null && !request.senhaHash().isBlank()) {
            profissional.setSenhaHash(request.senhaHash());
        }
        profissional.setAtivo(request.ativo());
        profissional.setCargoId(request.cargoId());
    }

    private void sincronizarVinculos(Profissional profissional, List<ProjetoVinculoRequisicao> projetos) {
        if (projetos == null) return;
        Set<Integer> projetosProcessados = new HashSet<>();
        profissionalProjetoRepositorio.deletarPorProfissionalId(profissional.getId());
        for (ProjetoVinculoRequisicao projetoVinculo : projetos) {
            if (!projetosProcessados.add(projetoVinculo.projetoId())) {
                throw new IllegalArgumentException("Projeto duplicado no cadastro do profissional.");
            }
            Projeto projeto = projetoRepositorio.findById(projetoVinculo.projetoId())
                    .orElseThrow(() -> new EntityNotFoundException("Projeto nao encontrado: " + projetoVinculo.projetoId()));
            ProfissionalProjeto vinculo = new ProfissionalProjeto();
            vinculo.setId(new ProfissionalProjetoId(projeto.getId(), profissional.getId()));
            vinculo.setProfissional(profissional);
            vinculo.setProjeto(projeto);
            vinculo.setValorHora(BigDecimal.valueOf(projetoVinculo.valorHora()));
            profissionalProjetoRepositorio.save(vinculo);
        }
    }
}