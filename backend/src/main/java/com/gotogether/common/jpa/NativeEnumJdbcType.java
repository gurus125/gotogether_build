package com.gotogether.common.jpa;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Minimal, dependency-free binder for a native Postgres enum column, used
 * alongside each entity's {@code Jpa} {@code AttributeConverter<TheEnum, String>}
 * (which lowercases the Java constant to match the Postgres label).
 *
 * <p>Exists because, in this exact Hibernate 6.5.3 + JDK 25 + Postgres 16
 * combination, both of Hibernate's own built-in mechanisms for this failed
 * (all confirmed 2026-07-22 against a real local Postgres, each only
 * surfacing at a different stage):
 *
 * <ol>
 *   <li>{@code @JdbcTypeCode(SqlTypes.OTHER)} resolved to Hibernate's
 *       {@code VarbinaryJdbcType} (a byte-array binder) instead of a
 *       text-based one — {@code ClassCastException}/{@code
 *       "Could not convert 'java.lang.String' to '[B'"} the first time a
 *       row was actually inserted.
 *   <li>{@code @JdbcType(PostgreSQLEnumJdbcType.class)} — the documented
 *       replacement for exactly this case — throws a
 *       {@code NullPointerException} in {@code addAuxiliaryDatabaseObjects}
 *       at application boot (metadata-build time, before Flyway/schema
 *       validation even run): it tries to auto-generate {@code CREATE TYPE}
 *       DDL by introspecting the Java enum's {@code .values()}, which this
 *       project doesn't need at all — Flyway, not Hibernate, owns every
 *       enum type's DDL (see the V1 migration).
 * </ol>
 *
 * <p>This class does only the one thing actually required: bind the
 * already-converted {@code String} via {@code PreparedStatement.setObject(
 * index, value, Types.OTHER)} (letting pgjdbc infer the cast against the
 * native enum column — the same thing every approach above needs to do
 * underneath, just without any enum-introspection machinery in the way), and
 * read it back with a plain {@code ResultSet.getString(...)}.
 */
public class NativeEnumJdbcType implements JdbcType {

    public static final NativeEnumJdbcType INSTANCE = new NativeEnumJdbcType();

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new ValueBinder<>() {
            @Override
            public void bind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setObject(index, value == null ? null : javaType.unwrap(value, String.class, options), Types.OTHER);
            }

            @Override
            public void bind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
                st.setObject(name, value == null ? null : javaType.unwrap(value, String.class, options), Types.OTHER);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return getJavaType().wrap(rs.getString(paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return getJavaType().wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return getJavaType().wrap(statement.getString(name), options);
            }
        };
    }
}
