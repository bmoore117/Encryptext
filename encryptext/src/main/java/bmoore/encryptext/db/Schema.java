package bmoore.encryptext.db;


public class Schema {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Encryptext.db";

    public static class key_exchange_statuses {
        public static final String DDL = "create table key_exchange_statuses (\n" +
                "\texchange_id integer primary key NOT NULL,\n" +
                "\tphone_number text NOT NULL UNIQUE,\n" +
                "\tname text NOT NULL,\n" +
                "\tstatus text NOT NULL,\n" +
                "\tstatus_date text\n" +
                ");";

        public static final String exchange_id = "exchange_id";
        public static final String phone_number = "phone_number";
        public static final String name = "name";
        public static final String status = "status";
        public static final String status_date = "status_date";
    }

    public static class contact_keys {

        public static final String DDL = "create table contact_keys (\n" +
                "    phone_number text primary key NOT NULL,\n" +
                "\tpublic_key blob,\n" +
                "\tprivate_key blob,\n" +
                "\tsecret_key blob,\n" +
                "\tFOREIGN KEY(phone_number) REFERENCES key_exchange_statuses(phone_number)\n" +
                ");";

        public static final String phone_number = "phone_number";
        public static final String public_key = "public_key";
        public static final String private_key = "private_key";
        public static final String secret_key = "secret_key";
    }

    public static class conversations {

        public static final String DDL = "create table conversations (\n" +
                "\tmessage_id integer NOT NULL primary key autoincrement,\n" +
                "\tphone_number text NOT NULL,\n" +
                "\tname text,\n" +
                "\tstatus_date text,\n" +
                "\tmessage text,\n" +
                "\tFOREIGN KEY(phone_number) REFERENCES contact_keys(phone_number)\n" +
                ");";

        public static final String message_id = "message_id";
        public static final String phone_number = "phone_number";
        public static final String name = "name";
        public static final String status_date = "status_date";
        public static final String message = "message";
    }
}
