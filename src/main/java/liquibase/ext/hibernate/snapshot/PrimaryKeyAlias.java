package liquibase.ext.hibernate.snapshot;

public interface PrimaryKeyAlias {

	String toAliasString(String tableName);

}
