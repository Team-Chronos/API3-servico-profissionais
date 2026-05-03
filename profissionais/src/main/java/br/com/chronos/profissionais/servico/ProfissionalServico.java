package br.com.chronos.profissionais.servico;

import br.com.chronos.profissionais.api.dto.ProfissionalRequisicao;
import br.com.chronos.profissionais.api.dto.ProfissionalResposta;
import br.com.chronos.profissionais.api.dto.ProjetoResumoResposta;
import br.com.chronos.profissionais.api.dto.ProjetoVinculoRequisicao;
import br.com.chronos.profissionais.api.dto.ProjetoVinculadoResposta;
import br.com.chronos.profissionais.dominio.Profissional;
import br.com.chronos.profissionais.dominio.ProfissionalProjeto;
import br.com.chronos.profissionais.dominio.ProfissionalProjetoId;
import br.com.chronos.profissionais.dominio.Projeto;
import br.com.chronos.profissionais.repositorio.ProfissionalProjetoRepositorio;
import br.com.chronos.profissionais.repositorio.ProfissionalRepositorio;
import br.com.chronos.profissionais.repositorio.ProjetoRepositorio;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Cacheable(value = "profissionais")
    public List<ProfissionalResposta> listar() {
        return profissionalRepositorio.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "profissional", key = "#id")
    public ProfissionalResposta buscarPorId(int id) {
        Profissional profissional = buscarProfissionalOuFalhar(id);
        return toResponse(profissional);
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
        return toResponse(salvo);
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
        return toResponse(salvo);
    }

    @Transactional
    @CacheEvict(value = { "profissionais", "profissional", "projetos-vinculados" }, allEntries = true)
    public void deletar(int id) {
        Profissional profissional = buscarProfissionalOuFalhar(id);
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

    @Cacheable(value = "projetos-vinculados", key = "#profissionalId")
    @Transactional(readOnly = true)
    public List<ProjetoVinculadoResposta> listarProjetosVinculados(int profissionalId) {
        buscarProfissionalOuFalhar(profissionalId);
        return listarProjetosVinculadosInterno(profissionalId);
    }

    @Cacheable(value = "projetos-disponiveis")
    @Transactional(readOnly = true)
    public List<ProjetoResumoResposta> listarProjetosDisponiveis() {
        return projetoRepositorio.findAll()
                .stream()
                .map(projeto -> new ProjetoResumoResposta(
                        projeto.getId(),
                        projeto.getNome(),
                        projeto.getCodigo(),
                        projeto.getValorHoraBase().doubleValue()))
                .toList();
    }

    private List<ProjetoVinculadoResposta> listarProjetosVinculadosInterno(int profissionalId) {
        return profissionalProjetoRepositorio.buscarPorProfissionalComProjeto(profissionalId)
                .stream()
                .map(v -> new ProjetoVinculadoResposta(
                        v.getProjeto().getId(),
                        v.getProjeto().getNome(),
                        v.getProjeto().getCodigo(),
                        v.getValorHora().doubleValue()))
                .toList();
    }

    private Profissional buscarProfissionalOuFalhar(int id) {
        return profissionalRepositorio.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profissional nao encontrado."));
    }

    private ProfissionalResposta toResponse(Profissional profissional) {
        return new ProfissionalResposta(
                profissional.getId(),
                profissional.getNome(),
                profissional.getEmail(),
                profissional.getAtivo(),
                profissional.getCargoId(),
                listarProjetosVinculadosInterno(profissional.getId()));
    }

    private void preencherDados(Profissional profissional, ProfissionalRequisicao request) {
        profissional.setNome(request.nome());
        profissional.setEmail(request.email());

        profissional.setAtivo(request.ativo());
        profissional.setCargoId(request.cargoId());
    }

    private void sincronizarVinculos(Profissional profissional, List<ProjetoVinculoRequisicao> projetos) {
        if (projetos == null)
            return;
        Set<Integer> projetosProcessados = new HashSet<>();
        profissionalProjetoRepositorio.deletarPorProfissionalId(profissional.getId());
        for (ProjetoVinculoRequisicao projetoVinculo : projetos) {
            if (!projetosProcessados.add(projetoVinculo.projetoId())) {
                throw new IllegalArgumentException("Projeto duplicado no cadastro do profissional.");
            }
            Projeto projeto = projetoRepositorio.findById(projetoVinculo.projetoId())
                    .orElseThrow(
                            () -> new EntityNotFoundException("Projeto nao encontrado: " + projetoVinculo.projetoId()));
            ProfissionalProjeto vinculo = new ProfissionalProjeto();
            vinculo.setId(new ProfissionalProjetoId(projeto.getId(), profissional.getId()));
            vinculo.setProfissional(profissional);
            vinculo.setProjeto(projeto);
            vinculo.setValorHora(BigDecimal.valueOf(projetoVinculo.valorHora()));
            profissionalProjetoRepositorio.save(vinculo);
        }
    }
}
