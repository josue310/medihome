# Déploiement gratuit de Medi Home

Architecture : **Render** (application, conteneur Docker Tomcat 7 / Java 8, HTTPS automatique)
+ **Aiven for MySQL** (base de données, offre gratuite).
Le HTTPS de Render est indispensable : sans lui, les navigateurs bloquent caméra et micro.

## 1. Base de données (Aiven)
1. Créer un compte sur https://aiven.io puis **Create service > MySQL > Free plan**.
2. Une fois le service démarré, noter dans **Overview > Connection information** :
   Host, Port, User (`avnadmin`), Password, Database (`defaultdb`).
3. L'URL JDBC à utiliser est :
   `jdbc:mysql://HOST:PORT/defaultdb?useSSL=true&requireSSL=true&verifyServerCertificate=false&useUnicode=true&characterEncoding=UTF-8`

Les tables sont créées automatiquement au premier démarrage de l'application.

## 2. Code sur GitHub
Dans le dossier `GESTIONHOPITAL` :
```
git init
git add .
git commit -m "Medi Home"
git branch -M main
git remote add origin https://github.com/<vous>/medihome.git
git push -u origin main
```
(Créer d'abord le dépôt vide `medihome` sur github.com, de préférence **privé**.)

## 3. Application (Render)
1. Créer un compte sur https://render.com (connexion avec GitHub).
2. **New + > Blueprint**, choisir le dépôt : Render lit `render.yaml`.
3. Renseigner les variables demandées :

| Variable | Valeur |
|---|---|
| `HOSPITAL_DB_URL` | l'URL JDBC de l'étape 1 |
| `HOSPITAL_DB_USER` | `avnadmin` |
| `HOSPITAL_DB_PASSWORD` | mot de passe Aiven |
| `ADMIN_EMAIL` | e-mail du compte administrateur à créer |
| `ADMIN_PASSWORD` | son mot de passe (8 caractères min.) |
| `TURN_URLS`, `TURN_USERNAME`, `TURN_CREDENTIAL` | facultatif, voir §5 |

4. **Apply**. Le premier déploiement prend 5 à 10 minutes. L'adresse finale est du type
   `https://medihome-xxxx.onrender.com`.
5. Dans **Logs**, vérifier : `Schéma de base vérifié`, `Administrateur créé`, `Server startup`.

## 4. Reprendre les données existantes (facultatif)
1. Après le premier déploiement (tables créées), lancer `deploy\exporter_donnees.bat`.
2. Importer le fichier produit dans Aiven :
```
C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe -h HOST -P PORT -u avnadmin -p --ssl-mode=REQUIRED defaultdb < %USERPROFILE%\medihome_donnees.sql
```
3. Supprimer `medihome_donnees.sql` (données personnelles).

## 5. Relais TURN (recommandé pour les appels entre réseaux mobiles)
Sans relais, deux personnes derrière certains réseaux (4G, entreprise) peuvent ne pas se voir.
Offre gratuite : https://www.metered.ca/tools/openrelay/ — créer une application, puis copier
les URL `turn:...` (séparées par des virgules) dans `TURN_URLS` et les identifiants dans
`TURN_USERNAME` / `TURN_CREDENTIAL`.

## Limites des offres gratuites
- Render met l'application en veille après 15 min sans visite : la première visite suivante
  prend environ 1 minute.
- Les documents déposés en téléconsultation sont stockés sur le disque du conteneur
  (`/tmp/uploads`) : ils sont **perdus à chaque redéploiement ou redémarrage**.
- Conditions des offres susceptibles d'évoluer : vérifier sur les sites de Render et d'Aiven.

## Mise à jour
Chaque `git push` sur `main` redéploie automatiquement l'application.
