package com.chubb.claims.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Home market. Null for managers, who see all markets. */
    @Enumerated(EnumType.STRING)
    private Market market;

    protected AppUser() {
    }

    public AppUser(String id, String name, Role role, Market market) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.market = market;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public Market getMarket() { return market; }
}
