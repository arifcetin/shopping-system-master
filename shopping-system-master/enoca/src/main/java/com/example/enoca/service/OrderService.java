package com.example.enoca.service;

import com.example.enoca.Dto.OrderResponse;
import com.example.enoca.entity.Cart;
import com.example.enoca.entity.Customer;
import com.example.enoca.entity.OrderC;
import com.example.enoca.repo.CartRepo;
import com.example.enoca.repo.CartTotalRepo;
import com.example.enoca.repo.CustomerRepo;
import com.example.enoca.repo.OrderRepo;
import com.example.enoca.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepo orderRepo;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomerRepo customerRepo;
    private final CartRepo cartRepo;
    private final CartService cartService;
    private final CartTotalRepo cartTotalRepo;
    @Autowired
    public OrderService(OrderRepo orderRepo, JwtTokenProvider jwtTokenProvider, CustomerRepo customerRepo, CartRepo cartRepo, CartService cartService, CartTotalRepo cartTotalRepo) {
        this.orderRepo = orderRepo;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customerRepo = customerRepo;
        this.cartRepo = cartRepo;
        this.cartService = cartService;
        this.cartTotalRepo = cartTotalRepo;
    }

    public ResponseEntity<OrderResponse> placeOrder(HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        List<Cart> cart = cartRepo.findCartsByCustomer(customer.get());
        for (Cart c :cart){
            OrderC newOrder = new OrderC();
            newOrder.setAmount(c.getAmount());
            newOrder.setCustomer(c.getCustomer());
            newOrder.setPrice(c.getPrice());
            newOrder.setPrId(c.getPrId());
            newOrder.setTotalProductPrice(c.getTotalProductPrice());
            orderRepo.save(newOrder);
        }
        OrderResponse orderResponse = getOrderResponseEntity("Order placed", orderRepo.findOrderCsByCustomer(customer.get()), cartTotalRepo.findCartTotalByCustomer(customer.get()).getTotalPrice());
        cartService.emptyCart(httpServletRequest);
        return new ResponseEntity<>(orderResponse, HttpStatus.OK);
    }
    public ResponseEntity<OrderResponse> getOrderForCode(HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        List<OrderC> orderCList = orderRepo.findOrderCsByCustomer(customer.get());
        if (!orderCList.isEmpty()){
            return new ResponseEntity<>(getOrderResponseEntity("Order", orderRepo.findOrderCsByCustomer(customer.get()), orderRepo.sumTotalProduct(customer.get().getCustomerId())),HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(getOrderResponseEntity("Order not found", null, null),HttpStatus.OK);
        }
    }

    public List<OrderC> getAllOrdersForCustomer() {
        return orderRepo.findAll();
    }
    private Optional<Customer> getCustomerByBearer(HttpServletRequest httpServletRequest) {
        String bearer = httpServletRequest.getHeader("Authorization");
        Long customerId = jwtTokenProvider.getUserIdFromJwt(bearer.substring("Bearer".length()+1));
        Optional<Customer> customer = customerRepo.findById(customerId);
        return customer;
    }
    private OrderResponse getOrderResponseEntity(String message, List<OrderC> orderCList, Float totalPrice){
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setMessage(message);
        orderResponse.setOrderCList(orderCList);
        orderResponse.setTotalPrice(totalPrice);
        return orderResponse;
    }
}
