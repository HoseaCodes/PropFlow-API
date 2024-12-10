package com.airbnb.model.transactions;

public enum TransactionCategory {
      // Income Categories
      BOOKING_PAYMENT("Booking Payment"),
      CLEANING_FEE("Cleaning Fee"),
      SECURITY_DEPOSIT("Security Deposit"),
      LATE_CHECKOUT_FEE("Late Checkout Fee"),
      ADDITIONAL_GUEST_FEE("Additional Guest Fee"),
      OTHER_INCOME("Other Income"),
  
      // Expense Categories
      CLEANING("Cleaning"),
      MAINTENANCE("Maintenance"),
      UTILITIES("Utilities"),
      SUPPLIES("Supplies"),
      INSURANCE("Insurance"),
      MORTGAGE("Mortgage"),
      PROPERTY_TAX("Property Tax"),
      PLATFORM_FEES("Platform Fees"),
      MARKETING("Marketing"),
      OTHER_EXPENSE("Other Expense"),
      TAXES("Taxes"),
      HOA_FEES("HOA Fees"),
      REPAIRS("Repairs"),
      FURNISHING("Furnishing"),
      PROPERTY_MANAGEMENT("Property Management"),
      PERMITS_LICENSES("Permits and Licenses"),
      LEGAL_PROFESSIONAL("Legal and Professional");
  
      private final String displayName;
  
      TransactionCategory(String displayName) {
          this.displayName = displayName;
      }
  
      public String getDisplayName() {
          return displayName;
      }
  
      // Helper method to determine if this is an income category
      public boolean isIncomeCategory() {
          return this == BOOKING_PAYMENT 
              || this == CLEANING_FEE 
              || this == SECURITY_DEPOSIT 
              || this == LATE_CHECKOUT_FEE 
              || this == ADDITIONAL_GUEST_FEE 
              || this == OTHER_INCOME;
      }
  
      // Helper method to determine if this is an expense category
      public boolean isExpenseCategory() {
          return !isIncomeCategory();
      }
  
      // Helper method to validate category based on transaction type
      public static boolean isValidForType(TransactionType type, TransactionCategory category) {
          return (type == TransactionType.INCOME && category.isIncomeCategory())
              || (type == TransactionType.EXPENSE && category.isExpenseCategory());
      }
}
