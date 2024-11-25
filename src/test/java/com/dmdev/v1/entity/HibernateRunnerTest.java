package com.dmdev.v1.entity;

import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.generate;

public class HibernateRunnerTest {
    private final static String DB_URL = "jdbc:postgresql://localhost:5432/users";
    private final static String DB_USER_NAME = "postgres";
    private final static String DB_USER_PASS = "postgres";

    /**
     * Через Reflection API получим класс сущности,
     * чтобы показать, как Session формирует SQL-запрос
     * (%s — признак динамической составляющей)
     */
    @Test
    void checkGetReflectionApi() throws SQLException, IllegalAccessException {
        User user = User.builder()
                .username("ivan@gmail.com")
                .firstname("Ivan")
                .lastname("Ivanov")
                .birthDate(LocalDate.of(2000, 1, 19))
                .age(24)
                .build();
        String sql = """
                INSERT INTO %s (%s) VALUES (%s);
                """;
        String tableName = Optional.ofNullable(user.getClass().getAnnotation(Table.class))
                .map(tableAnnotation -> "%s.%s".formatted(tableAnnotation.schema(), tableAnnotation.name()))
                .orElse(user.getClass().getName());

        Field[] declaredFields = user.getClass().getDeclaredFields(); // не гарантирует порядок полей

        String columnNames = Arrays.stream(declaredFields)
                .map(field -> Optional.ofNullable(field.getAnnotation(Column.class))
                        .map(Column::name)
                        .orElse(field.getName()))
                .sorted(Comparator.naturalOrder())
                .collect(joining(", "));

        String columnValues = generate(() -> "?")
                .limit(declaredFields.length)
                .collect(joining(", "));

        System.out.println(sql.formatted(tableName, columnNames, columnValues));

        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER_NAME, DB_USER_PASS);
             PreparedStatement preparedStatement = connection.prepareStatement(sql.formatted(tableName, columnNames, columnValues))
        ) {
            for (int i = 0; i < declaredFields.length; i++) {
                declaredFields[i].setAccessible(true);
                // получить значение поля, представленного этим Field, для указанного объекта (user)
                // user - объект, из которого должно быть извлечено значение представляемого поля
                // возвращает значение представляемого поля в объекте user;
                // примитивные значения упаковываются в соответствующий объект перед возвратом
                var object = declaredFields[i].get(user);
                preparedStatement.setObject(i + 1, object);
            }

            preparedStatement.executeUpdate();
        }
    }
}
