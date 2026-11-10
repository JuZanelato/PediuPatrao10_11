package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface AuditoriaRepository extends Repository<Auditoria, String> {
    Auditoria save(Auditoria auditoria);

    List<Auditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);

    long count();
}
