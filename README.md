# 💳 Eventy Transactions Service

Le **Transactions Service** est le composant critique gérant les flux financiers de la plateforme. Il orchestre l'achat de billets en validant les conditions (solde, disponibilité), en enregistrant la transaction et en déclenchant les mises à jour asynchrones vers les autres services.

## 🚀 Fonctionnalités

* **Achat de Billets** : Création et validation de transactions sécurisées.
* **Orchestration** :
    * **Vérification Synchrone** : Valide l'existence et le solde de l'acheteur (via Users Service) et la disponibilité du billet (via Tickets Service).
    * **Notification Asynchrone** : Notifie le système une fois le paiement validé pour mettre à jour les stocks et les soldes.
* **Historique** : Consultation de l'historique des achats et des ventes par utilisateur.
* **Calculs** : Gestion automatique des frais de plateforme et du montant net vendeur.

## 🛠️ Stack Technique

* **Langage** : Java 21
* **Framework** : Spring Boot 3.5.x
* **Base de données** : PostgreSQL 15
* **Communication** :
    * **OpenFeign** : Appels synchrones vers `users-service` et `tickets-service`.
    * **Spring Kafka** : Publication d'événements de domaine.
* **Découverte** : Netflix Eureka Client

## ⚙️ Installation et Démarrage

### Prérequis
* JDK 21
* Docker & Docker Compose (Infrastructure)

### Démarrage en local (Docker Compose)

Ce service nécessite que l'infrastructure (Eureka, Kafka, Postgres) et les services dépendants (Users, Tickets) soient disponibles.

# Depuis la racine du projet backend global
docker-compose up -d --build eventy-transactions-service

Le service sera accessible sur le port 8085.
🔧 Configuration


Variables d'environnement principales (docker-compose.yml) :
📚 API Reference


Transactions (/transactions)


POST /transactions : Créer une nouvelle transaction (Achat).
Body : { "ticketId": "...", "buyerId": "...", "amount": 50.0, "paymentMethod": "CB" }

GET /transactions/{id} : Détails d'une transaction.
GET /transactions/user/{userId} : Historique des transactions d'un utilisateur (en tant qu'acheteur ou vendeur).



🔄 Architecture & Flux de Données


Flux d'Achat (Synchronous Check + Eventual Consistency)


Requête (POST) : L'utilisateur initie un achat.
Vérification (Feign) :
Appel à TicketClient : Le billet est-il AVAILABLE ? Le prix est-il correct ?
Appel à UserClient : L'acheteur existe-t-il ? A-t-il les fonds ?

Persistance : La transaction est sauvegardée en base avec le statut COMPLETED (simulé pour le MVP).
Propagation (Kafka) :
Publication de TicketSoldEvent -> Le Ticket Service passe le billet à SOLD.
Publication de PaymentValidatedEvent -> Le User Service débite l'acheteur et crédite le vendeur.


© 2025 Eventy Project
