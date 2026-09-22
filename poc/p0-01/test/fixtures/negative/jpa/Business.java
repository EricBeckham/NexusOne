package business;
public final class Business { void run(EntityManager em) { em.createNativeQuery("select 1"); } }
