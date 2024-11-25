# PropFlow API 🏠

PropFlow API is a comprehensive property management system designed for Airbnb hosts in Dallas, Texas. It helps hosts manage their properties, track expenses, handle bookings, and maintain cleaning schedules while ensuring compliance with local regulations.

## 🚀 Features

- **Property Management**
  - CRUD operations for properties
  - Property details and pricing management
  - STR permit tracking
  - Photo management

- **Booking Management**
  - Reservation tracking
  - Check-in/check-out management
  - Guest communication logs
  - Occupancy tracking

- **Financial Management**
  - Expense tracking
  - Revenue monitoring
  - Break-even analysis
  - Financial projections
  - Hotel occupancy tax calculations

- **Cleaning Management**
  - Cleaning schedule
  - Customizable checklists
  - Service provider management
  - Quality tracking

## 🛠️ Tech Stack

### Backend
- Java 17
- Spring Boot 3.2.0
- Spring Security
- Spring Data JPA
- PostgreSQL
- Maven
- JWT Authentication

### Frontend
- Angular 16+
- TypeScript
- Tailwind CSS
- Node.js 18+
- npm

## 📋 Prerequisites

- JDK 17 or later
- Node.js 18.x or later
- PostgreSQL 13 or later
- Maven 3.8+
- Git

## 🔧 Installation

### Backend Setup

1. Clone the repository
```bash
git clone https://github.com/yourusername/PropFlow-API.git
cd PropFlow-API
```

2. Configure PostgreSQL
```sql
CREATE DATABASE airbnb_management;
```

3. Update application.properties
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/airbnb_management
spring.datasource.username=your_username
spring.datasource.password=your_password
```

4. Build and run the backend
```bash
mvn clean install
mvn spring-boot:run
```

## 🔒 Environment Variables

Create a `.env` file in the root directory and add the following:

```env
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=airbnb_management
DB_USERNAME=your_username
DB_PASSWORD=your_password

# JWT Configuration
JWT_SECRET=your_jwt_secret
JWT_EXPIRATION=86400000

# Server Configuration
SERVER_PORT=8080
```

## 📚 API Documentation

### Base URL
```
http://localhost:8080/api
```

### Endpoints

#### Properties
- `GET /properties` - Get all properties
- `GET /properties/{id}` - Get property by ID
- `POST /properties` - Create new property
- `PUT /properties/{id}` - Update property
- `DELETE /properties/{id}` - Delete property

#### Bookings
- `GET /bookings` - Get all bookings
- `POST /bookings` - Create new booking
- `PUT /bookings/{id}` - Update booking status

#### Expenses
- `GET /expenses` - Get all expenses
- `POST /expenses` - Add new expense
- `GET /expenses/summary` - Get expense summary

## 🧪 Running Tests

### Backend Tests
```bash
mvn test
```

### Frontend Tests
```bash
ng test
```

## 🚀 Deployment

1. Build the backend
```bash
mvn clean package
```

2. Build the frontend
```bash
ng build --prod
```

3. Deploy the generated artifacts to your server

## 📜 Dallas STR Regulations

Important regulations for Dallas Airbnb hosts:
- STR Permit required for rentals ≤ 30 days
- 7% hotel occupancy tax collection required
- Compliance with local zoning restrictions
- Written landlord permission required for rental properties

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

## 👥 Support

For support, email support@PropFlow.api or join our Slack channel.

## ✨ Acknowledgements

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Angular](https://angular.io/)
- [Tailwind CSS](https://tailwindcss.com/)
- [PostgreSQL](https://www.postgresql.org/)

## 🛣️ Roadmap

- [ ] Mobile application
- [ ] Integration with Airbnb API
- [ ] Smart pricing optimization
- [ ] Automated guest communication
- [ ] Advanced analytics dashboard
- [ ] Multi-language support