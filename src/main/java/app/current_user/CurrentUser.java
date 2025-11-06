package app.current_user;

public class CurrentUser {
    private static int id;
    private static String name;

    public static int getId() { return id; }
    public static void setId(int id) { CurrentUser.id = id; }

    public static String getName() { return name; }
    public static void setName(String name) { CurrentUser.name = name; }
}
