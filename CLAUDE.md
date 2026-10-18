# Instrucciones para Claude en NovaGest

Este archivo lo lee Claude (o cualquier asistente) al trabajar en este repositorio. Resume como trabaja el
equipo para que la ayuda salga igual sin importar quien la pida (Ernesto, Brandom o Frank).

## Que es NovaGest
Sistema de tramites ciudadanos del curso de Auditoria de Sistemas. Participan tres grupos: desarrollo (este
repositorio), auditoria (revisa los controles) y servidores (despliega el sistema). Por eso importan tanto
los permisos, la bitacora, las pruebas y que el sistema se levante facil.

- Backend: Spring Boot 4, Java 21, PostgreSQL, Flyway, JWT. Codigo en `src/`.
- Frontend: plan y tarjetas en `docs/frontend/README.md` (leerlo antes de tocar el front).
- Reglas del equipo: `CONTRIBUTING.md` (manda sobre este archivo si algo choca).
- Tablero de tarjetas: Notion, pagina NovaGest. Cada tarjeta tiene numero, "Hecho cuando" y responsable.

## Como pedir ayuda (para el equipo)
Lo que mejor funciona es nombrar la tarjeta y pegar lo que dice el tablero:
> Trabaja la tarjeta 15 de NovaGest. Hecho cuando: <texto de Notion>. Depende de: <tarjetas>.

Para el front: "Trabaja la tarjeta F5 segun docs/frontend/README.md".

## Antes de programar una tarjeta (para Claude)
1. Confirmar el numero de tarjeta, que pide ("Hecho cuando") y quien es el responsable. Si el responsable es
   otra persona, avisar antes de empezar para no duplicar trabajo.
2. Revisar si ya existe algo: ramas `origin/feature/NN-*`, codigo y pruebas que mencionen "Tarjeta NN".
3. Partir de `develop` actualizado. Rama `feature/NN-descripcion` o `fix/NN-descripcion`.
4. Seguir el patron del modulo mas parecido (por ejemplo `tramites/` o `usuarios/`), no inventar estructura.

## Reglas del codigo (resumen de CONTRIBUTING)
- Controlador solo recibe, valida y responde; la logica va en el servicio con `@Transactional`.
- Nunca se devuelven entidades JPA: siempre DTO (`record`).
- Cada endpoint lleva `@PreAuthorize("hasAuthority('recurso:accion')")` o `@SoloAutenticado`. La prueba
  `MatrizDeRolesTests` falla si falta.
- Toda accion que crea, edita o elimina llama a `BitacoraService.registrar(...)` en la misma transaccion,
  sin correos, nombres, claves ni datos personales en antes/despues.
- Errores: `ResponseStatusException` (404, 409) o `DatosInvalidosException` (400); salen como Problem Details.
- El esquema cambia solo con migraciones Flyway con version unica (revisar la ultima en `develop`).
- Comentarios y mensajes en espanol sin tildes, igual que el resto del codigo.
- Commits cortos en imperativo con el numero de tarjeta: `12 Agregar administracion de usuarios y roles`.

## Pruebas
- Cada tarjeta trae su clase de pruebas: caso feliz, al menos un permiso denegado (403) y errores (400/404/409).
- Las pruebas usan Testcontainers: solo hace falta Docker Desktop encendido, no el `.env`.
- Correr: `.\mvnw.cmd test "-Dtest=MiClaseTests,MatrizDeRolesTests"` y antes del PR `.\mvnw.cmd clean verify`.
- Las pruebas son `@Transactional` y comparten la transaccion con la API: para preparar datos usar los
  repositorios/entidades, no `UPDATE` por SQL directo (Hibernate no ve el cambio).
- `MatrizDeRolesTests` llama a cada endpoint con el id 999999; un endpoint nuevo no debe dar 500 con un id
  inexistente.

## Problemas conocidos en las maquinas del equipo
- Proyecto dentro de OneDrive: `clean` puede fallar con "Failed to delete ...\target". Pausar OneDrive,
  cerrar VS Code o correr `.\mvnw.cmd verify` sin `clean`. Mejor clonar fuera de OneDrive (`C:\proyectos`).
- "Could not find a valid Docker environment": Docker Desktop no esta encendido.

## Entregar
- PR siempre hacia `develop` con la plantilla completa. Revisor segun la tabla "Quien revisa a quien" de
  CONTRIBUTING (PR de Ernesto lo revisa Brandom, PR de Brandom lo revisa Frank, PR de Frank lo revisa Ernesto).
- Si se toco codigo de otra tarjeta, explicarlo en "Notas para quien revisa".
