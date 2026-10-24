package cl.finanzas.personales.repository;

import cl.finanzas.personales.model.MedioPago;
import cl.finanzas.personales.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long>, JpaSpecificationExecutor<Transaccion> {
    @Query("SELECT t.nombreRecurrente FROM Transaccion t WHERE t.user.id = :userId "
            + "AND t.esRecurrente = true AND t.nombreRecurrente IS NOT NULL")
    List<String> findNombresRecurrentesByUserId(@Param("userId") Long userId);

    long countByCategoriaId(Long categoriaId);

    long countBySubcategoriaId(Long subcategoriaId);

    /**
     * Suma neta de los movimientos de una cuenta anteriores a {@code fechaCorte}: ingresos suman,
     * egresos restan y las transferencias entre cuentas propias no mueven nada.
     *
     * Solo se cuentan los medios que consumen dinero propio, para que una compra con tarjeta no
     * reste del saldo hasta el día en que se paga la tarjeta. Los medios se reciben como
     * parámetro (y no escritos en el JPQL) para que la consulta use exactamente la regla de
     * {@link MedioPago#consumeDineroPropio()}.
     *
     * El corte es estricto (menor que la fecha), que es lo que necesita el arrastre entre
     * periodos: los movimientos del propio periodo no van en el saldo con el que abre.
     *
     * No hace falta índice propio: {@code ix_transaccion_user_fecha (user_id, fecha)} acota por
     * usuario y fecha, y {@code ix_transaccion_cuenta_id} filtra la cuenta sobre ese recorte.
     * Medido con EXPLAIN ANALYZE sobre los datos de hoy (355 filas): la consulta por cuenta
     * resuelve por Bitmap Index Scan sobre ix_transaccion_cuenta_id y la agrupada hace Seq Scan,
     * las dos por debajo de 0.2 ms. Sobre una tabla así de chica otro índice solo agrega costo
     * de escritura, y se justificaría solo si el EXPLAIN mostrara muchas filas por usuario.
     */
    @Query("""
            SELECT COALESCE(SUM(CASE
                WHEN t.tipo = cl.finanzas.personales.model.TipoTransaccion.INGRESO THEN t.monto
                WHEN t.tipo = cl.finanzas.personales.model.TipoTransaccion.EGRESO THEN -t.monto
                ELSE 0
            END), 0)
            FROM Transaccion t
            WHERE t.user.id = :userId AND t.cuenta.id = :cuentaId AND t.fecha < :fechaCorte
              AND t.medio IN :medios
            """)
    BigDecimal saldoAcumuladoHasta(@Param("userId") Long userId,
                                   @Param("cuentaId") Long cuentaId,
                                   @Param("fechaCorte") LocalDate fechaCorte,
                                   @Param("medios") Collection<MedioPago> medios);

    /**
     * El mismo acumulado agrupado por cuenta, en una sola consulta: el arrastre del usuario es
     * la suma de todas sus cuentas, no una consulta por cuenta.
     */
    @Query("""
            SELECT t.cuenta.id AS cuentaId,
                   COALESCE(SUM(CASE
                       WHEN t.tipo = cl.finanzas.personales.model.TipoTransaccion.INGRESO THEN t.monto
                       WHEN t.tipo = cl.finanzas.personales.model.TipoTransaccion.EGRESO THEN -t.monto
                       ELSE 0
                   END), 0) AS saldo
            FROM Transaccion t
            WHERE t.user.id = :userId AND t.fecha < :fechaCorte
              AND t.medio IN :medios
            GROUP BY t.cuenta.id
            """)
    List<SaldoPorCuenta> saldosAcumuladosHasta(@Param("userId") Long userId,
                                               @Param("fechaCorte") LocalDate fechaCorte,
                                               @Param("medios") Collection<MedioPago> medios);
}
