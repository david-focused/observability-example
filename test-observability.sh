#!/bin/bash

# Color formatting for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

###########################################
# UTILITY FUNCTIONS
###########################################

press_to_continue() {
  echo
  read -p "Press any key to continue..." -n1 -s
  echo
  clear
}

check_services() {
  echo -e "${GREEN}Checking if services are accessible...${NC}"
  
  # Function to check a service's health
  check_service_health() {
    local service_name=$1
    local port=$2
    
    # Try health endpoint first
    local health_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health 2>/dev/null || echo "Connection failed")
    if [ "$health_status" == "200" ] || [ "$health_status" == "404" ]; then
      echo -e "${GREEN}✓ $service_name${NC}"
      return 0
    else
      # Try a simple ping to the root if actuator health fails
      local ping_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/ 2>/dev/null || echo "Connection failed")
      if [ "$ping_status" == "200" ] || [ "$ping_status" == "404" ] || [ "$ping_status" == "403" ]; then
        echo -e "${YELLOW}⚠ $service_name is accessible but health endpoint may not be enabled${NC}"
        return 0
      else
        echo -e "${RED}❌ $service_name is not accessible. Status: $health_status${NC}"
        echo "Make sure you've run 'make port-forward-services' to set up port forwarding"
        return 1
      fi
    fi
  }
  
  check_service_health "Order service" 8080 || return 1
  check_service_health "Shipping service" 8081 || return 1
  check_service_health "Inventory service" 8082 || return 1
  
  echo -e "${GREEN}All services are accessible.${NC}"
  return 0
}

open_jaeger_ui() {
  echo -e "${YELLOW}Attempting to open Jaeger UI in your default browser...${NC}"
  
  # Try to detect the OS and open the browser accordingly
  if [[ "$OSTYPE" == "darwin"* ]]; then
    open "http://localhost:16686"
  elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    xdg-open "http://localhost:16686" || sensible-browser "http://localhost:16686" || echo "Could not open browser automatically. Please open http://localhost:16686 manually."
  else
    echo "Could not detect OS. Please open http://localhost:16686 in your browser manually."
  fi
}

show_jaeger_info() {
  echo -e "${BOLD}${YELLOW}=== Observability with Jaeger ===${NC}"
  echo -e "View traces in real-time at ${BLUE}http://localhost:16686${NC}"
  echo
}

###########################################
# TEST SCENARIO FUNCTIONS
###########################################

# create a normal order (happy path)
run_happy_path() {
  echo -e "${BOLD}${BLUE}=== Happy Path Test ===${NC}"
  echo -e "Creating a normal order that completes successfully"
  echo -e "${GREEN}This will demonstrate a complete trace with all services working correctly${NC}"
  echo

  local product_id="test-product-$(date +%s)"
  local quantity=5
  
  echo -e "${YELLOW}Request:${NC} POST http://localhost:8080/orders/create"
  echo -e "${YELLOW}Body:${NC} {\"productId\":\"$product_id\",\"amount\":$quantity}"
  echo
  
  echo -e "${BLUE}Response:${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"$product_id\",\"amount\":$quantity}" \
    http://localhost:8080/orders/create
  echo -e "\n"
  
  echo -e "${YELLOW}This trace will show:${NC}"
  echo "• Order service creating an order"
  echo "• Inventory service checking and reserving inventory"
  echo "• Shipping service creating a shipment"
  echo
}

# create orders with some triggering a delay
run_delay_path() {
  echo -e "${BOLD}${BLUE}=== Service Latency Test ===${NC}"
  echo -e "Creating an order with intentional service latency"
  echo -e "${GREEN}This will demonstrate how delays appear in traces${NC}"
  echo

  local product_id="test-product-$(date +%s)-with-delay"
  local quantity=3
  
  echo -e "${YELLOW}Request:${NC} POST http://localhost:8080/orders/create"
  echo -e "${YELLOW}Body:${NC} {\"productId\":\"$product_id\",\"amount\":$quantity}"
  echo
  
  echo -e "${BLUE}Response:${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"$product_id\",\"amount\":$quantity}" \
    http://localhost:8080/orders/create
  echo -e "\n"
  
  echo -e "${YELLOW}This trace will show:${NC}"
  echo "• The same services as the happy path"
  echo "• The inventory service spans taking 1 second longer"
  echo "• The total order process taking longer due to service latency"
  echo
}

# create orders with some triggering exceptions
run_exception_path() {
  echo -e "${BOLD}${BLUE}=== Error Path Test ===${NC}"
  echo -e "Creating an order that triggers an error in the inventory service"
  echo -e "${GREEN}This will demonstrate how errors appear in traces${NC}"
  echo

  local product_id="test-product-$(date +%s)-with-error"
  local quantity=2
  
  echo -e "${YELLOW}Request:${NC} POST http://localhost:8080/orders/create"
  echo -e "${YELLOW}Body:${NC} {\"productId\":\"$product_id\",\"amount\":$quantity}"
  echo
  
  echo -e "${BLUE}Response:${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"$product_id\",\"amount\":$quantity}" \
    http://localhost:8080/orders/create
  echo -e "\n"
  
  echo -e "${YELLOW}This trace will show:${NC}"
  echo "• The order service starting to process an order"
  echo "• The inventory service responding with an error"
  echo "• The error being propagated back to the order service"
  echo "• Error states and exception details in the trace"
  echo
}

# run a multi-scenario test
run_multi_scenario() {
  echo -e "${BOLD}${BLUE}=== Multiple Scenario Test ===${NC}"
  echo -e "Creating multiple orders to demonstrate different patterns in traces"
  echo -e "${GREEN}This will help compare different scenarios side by side in Jaeger${NC}"
  echo
  
  echo -e "${YELLOW}Creating 3 orders:${NC}"
  
  # Normal order
  echo -e "1. ${GREEN}Normal order${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"normal-$(date +%s)\",\"amount\":3}" \
    http://localhost:8080/orders/create > /dev/null
    
  # Delay order
  echo -e "2. ${YELLOW}Order with delay${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"test-$(date +%s)-with-delay\",\"amount\":4}" \
    http://localhost:8080/orders/create > /dev/null
    
  # Error order
  echo -e "3. ${RED}Order with error${NC}"
  curl -s -X POST \
    -H "Content-Type: application/json" \
    -d "{\"productId\":\"test-$(date +%s)-with-error\",\"amount\":2}" \
    http://localhost:8080/orders/create > /dev/null
  
  echo -e "\n${GREEN}All requests completed!${NC}"
  echo
  echo -e "${YELLOW}In Jaeger, you can now:${NC}"
  echo "• Compare the three traces side by side"
  echo "• See the different durations of the operations"
  echo "• Identify which spans contain errors"
  echo "• Examine how delays and errors propagate through the system"
  echo
}

###########################################
# MENU AND PROGRAM FLOW
###########################################

show_menu() {
  echo
  echo -e "${BOLD}${GREEN}=== Observability Demo Menu ===${NC}"
  echo -e "${BLUE}Choose a test scenario:${NC}"
  echo "1) Happy Path - Create a successful order (normal trace)"
  echo "2) Latency Test - Create an order with service delays (latency analysis)"
  echo "3) Error Test - Create an order that triggers exceptions (error tracing)"
  echo "4) Multi-Scenario Test - Run all patterns for side-by-side comparison"
  echo "5) Open Jaeger UI (if available)"
  echo "0) Exit"
  echo
  read -p "Enter your choice [1-5]: " choice
  
  case $choice in
    1) clear; run_happy_path; press_to_continue ;;
    2) clear; run_delay_path; press_to_continue ;;
    3) clear; run_exception_path; press_to_continue ;;
    4) clear; run_multi_scenario; press_to_continue ;;
    5) open_jaeger_ui; press_to_continue ;;
    0) echo "Exiting..."; exit 0 ;;
    *) echo -e "${RED}Invalid option. Please try again.${NC}"; press_to_continue ;;
  esac
}

###########################################
# MAIN PROGRAM
###########################################

clear

if ! check_services; then
  echo -e "${RED}Service check failed. Please ensure all services are running and port-forwarding is set up.${NC}"
  echo "Run 'make port-forward-services' and try again."
  exit 1
fi

while true; do
  show_menu
done