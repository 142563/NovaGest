package com.proyecto.tramites.auditoria.repository;

import com.proyecto.tramites.auditoria.entity.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Solo se inserta y se consulta. La bitacora no se edita ni se borra: los metodos de modificacion existen en
 * JpaRepository, pero la entidad es inmutable y un trigger de la base rechaza UPDATE, DELETE y TRUNCATE.
 */
public interface BitacoraRepository extends JpaRepository<Bitacora, Long>, JpaSpecificationExecutor<Bitacora> {

}
