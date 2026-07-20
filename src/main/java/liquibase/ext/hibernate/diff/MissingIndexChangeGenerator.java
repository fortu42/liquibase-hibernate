package liquibase.ext.hibernate.diff;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.ext.hibernate.database.HibernateDatabase;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Relation;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Since Liquibase 6 the index backing a primary key or unique constraint is emitted as a standalone
 * {@code createIndex} in addition to the {@code addPrimaryKey}/{@code addUniqueConstraint} that already
 * creates it (Liquibase 5 kept it implicit in the constraint). That extra index is redundant and, on some
 * databases (e.g. Oracle, ORA-01408), an error.
 * <p/>
 * When comparing against a {@link HibernateDatabase} we suppress such redundant createIndex changes: a missing
 * index whose columns match a primary key or unique constraint on the same table is already created by that
 * constraint. Standalone indexes (e.g. {@code @Table(indexes = @Index(...))}) that do not match a constraint are
 * left untouched.
 */
public class MissingIndexChangeGenerator extends liquibase.diff.output.changelog.core.MissingIndexChangeGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (Index.class.isAssignableFrom(objectType)) {
            return PRIORITY_ADDITIONAL;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Change[] fixMissing(DatabaseObject missingObject, DiffOutputControl control, Database referenceDatabase, Database comparisonDatabase, ChangeGeneratorChain chain) {
        if ((referenceDatabase instanceof HibernateDatabase || comparisonDatabase instanceof HibernateDatabase)
                && missingObject instanceof Index
                && backsPrimaryKeyOrUniqueConstraint((Index) missingObject)) {
            return null;
        }
        return super.fixMissing(missingObject, control, referenceDatabase, comparisonDatabase, chain);
    }

    /**
     * Returns true if the index covers exactly the same set of columns as the table's primary key or one of its
     * unique constraints, i.e. it is the backing index of that constraint.
     */
    private boolean backsPrimaryKeyOrUniqueConstraint(Index index) {
        Relation relation = index.getRelation();
        if (!(relation instanceof Table)) {
            return false;
        }
        Table table = (Table) relation;

        Set<String> indexColumns = columnNames(index.getColumns());
        if (indexColumns.isEmpty()) {
            return false;
        }

        PrimaryKey pk = table.getPrimaryKey();
        if (pk != null && indexColumns.equals(columnNames(pk.getColumns()))) {
            return true;
        }
        for (UniqueConstraint uc : table.getUniqueConstraints()) {
            if (indexColumns.equals(columnNames(uc.getColumns()))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> columnNames(List<Column> columns) {
        Set<String> names = new LinkedHashSet<>();
        if (columns != null) {
            for (Column column : columns) {
                names.add(column.getName() == null ? null : column.getName().toUpperCase());
            }
        }
        return names;
    }
}
