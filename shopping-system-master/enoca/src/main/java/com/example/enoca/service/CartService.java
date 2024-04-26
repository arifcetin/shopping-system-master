package com.example.enoca.service;

import com.example.enoca.Dto.CartDto;
import com.example.enoca.Dto.CartResponse;
import com.example.enoca.entity.Cart;
import com.example.enoca.entity.CartTotal;
import com.example.enoca.entity.Customer;
import com.example.enoca.entity.Product;
import com.example.enoca.repo.CartRepo;
import com.example.enoca.repo.CartTotalRepo;
import com.example.enoca.repo.CustomerRepo;
import com.example.enoca.repo.ProductRepo;
import com.example.enoca.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepo cartRepo;
    private final ProductRepo productRepo;
    private final CustomerRepo customerRepo;
    private final JwtTokenProvider jwtTokenProvider;
    private final CartTotalRepo cartTotalRepo;
    @Autowired
    public CartService(CartRepo cartRepo, ProductRepo productRepo, CustomerRepo customerRepo, JwtTokenProvider jwtTokenProvider, CartTotalRepo cartTotalRepo) {
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.customerRepo = customerRepo;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cartTotalRepo = cartTotalRepo;
    }
    public ResponseEntity<CartResponse> addProductToCart(CartDto cartDto, HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        Optional<Product> product = productRepo.findById(cartDto.getpId());
        if (cartDto.getAmount() <= product.get().getStock()){
            product.get().setStock(product.get().getStock()-cartDto.getAmount());
            productRepo.save(product.get());
            if (customer.isPresent()){
                Cart cart = cartRepo.findCartByPrIdAndCustomer(cartDto.getpId(),customer.get());
                if(cart != null){
                    cart.setAmount(cart.getAmount()+cartDto.getAmount());
                    cart.setTotalProductPrice(cart.getTotalProductPrice()+(cartDto.getAmount()*productRepo.findById(cartDto.getpId()).get().getPrice()));
                    cartRepo.save(cart);
                    return getCartResponseResponseEntity("product added", getCarts(httpServletRequest).getBody().getCart(), cartTotal(customer.get()).getTotalPrice(), HttpStatus.OK);
                }
                Cart newCart = createNewCart(customer,cartDto);
                cartRepo.save(newCart);
                cartTotal(customer.get());
                return getCartResponseResponseEntity("new product added", getCarts(httpServletRequest).getBody().getCart(), cartTotal(customer.get()).getTotalPrice(), HttpStatus.OK);
            }
        }
        return getCartResponseResponseEntity("not enough stock", null, null, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<CartResponse> getCartResponseResponseEntity(String message, List<Cart> httpServletRequest, Float totalPrice, HttpStatus httpStatus) {
        CartResponse cartResponse = new CartResponse();
        cartResponse.setMessage(message);
        cartResponse.setCart(httpServletRequest);
        cartResponse.setTotalPrice(totalPrice);
        return new ResponseEntity<>(cartResponse, httpStatus);
    }

    private Optional<Customer> getCustomerByBearer(HttpServletRequest httpServletRequest) {
        String bearer = httpServletRequest.getHeader("Authorization");
        Long customerId = jwtTokenProvider.getUserIdFromJwt(bearer.substring("Bearer".length()+1));
        Optional<Customer> customer = customerRepo.findById(customerId);
        return customer;
    }

    public Cart createNewCart(Optional<Customer> customer, CartDto cartDto){
        Cart newCart = new Cart();
        newCart.setCustomer(customer.get());
        newCart.setPrId(cartDto.getpId());
        newCart.setAmount(cartDto.getAmount());
        newCart.setPrice(productRepo.findById(cartDto.getpId()).get().getPrice());
        newCart.setTotalProductPrice(cartDto.getAmount()*productRepo.findById(cartDto.getpId()).get().getPrice());
        return newCart;
    }

    public CartTotal cartTotal(Customer customer){
        CartTotal cartTotal = cartTotalRepo.findCartTotalByCustomer(customer);
        if (cartTotal==null){
            CartTotal newCartTotal = new CartTotal();
            newCartTotal.setCustomer(customer);
            newCartTotal.setTotalPrice(cartRepo.sumTotalProduct(customer.getCustomerId()));
            cartTotalRepo.save(newCartTotal);
            return newCartTotal;
        }
        else {
            cartTotal.setCustomer(customer);
            cartTotal.setTotalPrice(cartRepo.sumTotalProduct(customer.getCustomerId()));
            cartTotalRepo.save(cartTotal);
            return cartTotal;
        }
    }

    public ResponseEntity<CartResponse> removeProductFromCart(CartDto cartDto, HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        Optional<Product> product = productRepo.findById(cartDto.getpId());
        Cart cart = cartRepo.findCartByPrIdAndCustomer(cartDto.getpId(),customer.get());
        CartTotal cartTotal = cartTotalRepo.findCartTotalByCustomer(customer.get());
        if(cart.getAmount()==cartDto.getAmount()){
            cartTotal.setTotalPrice(cartTotal.getTotalPrice() - (cartDto.getAmount() * product.get().getPrice()));
            cartTotalRepo.save(cartTotal);
            cartRepo.deleteById(cart.getCartId());
            return getCartResponseResponseEntity("product deleted", getCarts(httpServletRequest).getBody().getCart(), cartTotal.getTotalPrice(), HttpStatus.OK);
        }
        else {
            cart.setAmount(cart.getAmount()-cartDto.getAmount());
            cart.setTotalProductPrice(cart.getTotalProductPrice()-(cartDto.getAmount()*product.get().getPrice()));
            cartTotal.setTotalPrice(cartTotal.getTotalPrice() - (cartDto.getAmount() * product.get().getPrice()));
            cartRepo.save(cart);
            cartTotalRepo.save(cartTotal);
            return getCartResponseResponseEntity("product deleted", getCarts(httpServletRequest).getBody().getCart(), cartTotal.getTotalPrice(),HttpStatus.OK);
        }
    }

    public ResponseEntity<CartResponse> getCarts(HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        CartTotal cartTotal = cartTotalRepo.findCartTotalByCustomer(customer.get());
        List<Cart> carts = cartRepo.findCartsByCustomer(customer.get());
        if(cartTotal!=null){
            return getCartResponseResponseEntity("Customers cart", carts, cartTotal.getTotalPrice(), HttpStatus.OK);
        }
        else {
            return getCartResponseResponseEntity("Customers cart not found", null,null, HttpStatus.OK);
        }
    }
    @Transactional
    public ResponseEntity<CartResponse> emptyCart(HttpServletRequest httpServletRequest) {
        final Optional<Customer> customer = getCustomerByBearer(httpServletRequest);
        CartTotal cartTotal = cartTotalRepo.findCartTotalByCustomer(customer.get());
        if(cartTotal!=null){
            cartRepo.deleteByCustomer(customer.get());
            cartTotalRepo.deleteByCustomer(customer.get());
            return getCartResponseResponseEntity("Cart deleted", null, null, HttpStatus.OK);
        }
        else {
            return getCartResponseResponseEntity("Customers cart not found", null, null, HttpStatus.OK);
        }
    }

    public void updateCart(Long pId){
        List<Cart> carts = cartRepo.findCartByPrId(pId);
        for (Cart c: carts){
            if(c!=null) {
                Customer customer = c.getCustomer();
                Optional<Product> product = productRepo.findById(pId);
                CartTotal cartTotal = cartTotalRepo.findCartTotalByCustomer(customer);
                c.setPrice(product.get().getPrice());
                c.setTotalProductPrice(c.getAmount() * c.getPrice());
                cartRepo.save(c);
                cartTotal(customer);
            }
        }
    }
}
