package br.com.chronos.profissionais.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.chronos.profissionais.api.dto.ProfissionalRequisicao;
import br.com.chronos.profissionais.api.dto.ProfissionalResposta;
import br.com.chronos.profissionais.api.dto.VinculoProjetoRequisicao;
import br.com.chronos.profissionais.servico.ProfissionalServico;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profissionais")
@CrossOrigin(origins = "*")
public class ProfissionalControlador {

    private final ProfissionalServico profissionalServico;

    public ProfissionalControlador(ProfissionalServico profissionalServico) {
        this.profissionalServico = profissionalServico;
    }



    @PostMapping
    public ResponseEntity<ProfissionalResposta> criar(@Valid @RequestBody ProfissionalRequisicao request) {
        ProfissionalResposta criado = profissionalServico.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ProfissionalResposta atualizar(@PathVariable int id, @Valid @RequestBody ProfissionalRequisicao request) {
        return profissionalServico.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable int id) {
        profissionalServico.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/projetos/{projetoId}")
    public ResponseEntity<Void> vincularProjeto(@PathVariable int id,
                                                @PathVariable int projetoId,
                                                @Valid @RequestBody VinculoProjetoRequisicao request) {
        profissionalServico.vincularProjeto(id, projetoId, request.valorHora());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/projetos/{projetoId}")
    public ResponseEntity<Void> desvincularProjeto(@PathVariable int id, @PathVariable int projetoId) {
        profissionalServico.desvincularProjeto(id, projetoId);
        return ResponseEntity.noContent().build();
    }


    @org.springframework.web.bind.annotation.ExceptionHandler({EntityNotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> tratarErrosDeRegra(RuntimeException ex) {
        HttpStatus status = ex instanceof EntityNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("erro", ex.getMessage()));
    }
}
