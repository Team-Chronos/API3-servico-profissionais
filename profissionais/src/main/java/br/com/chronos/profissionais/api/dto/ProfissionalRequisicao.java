package br.com.chronos.profissionais.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfissionalRequisicao(
                @NotBlank @Size(max = 256) String nome,
                @NotBlank @Email @Size(max = 256) String email,
                @Size(max = 255) String senhaHash,
                boolean ativo,
                int cargoId,
                List<@Valid ProjetoVinculoRequisicao> projetos) {
}
